"""Uploads a signed app bundle to Google Play and puts it on a track.

Run through `bazel run //watchface:upload`, which passes --package, --version-code and
--version-name; see --help. Talks to the Google Play Developer API (v3) as a service
account, with the Python standard library and `openssl` (to sign the token request), so it
needs no Google client libraries.

One run is one Play "edit": create it, upload the bundle, set the track's release, commit.
Nothing changes on Play unless the commit succeeds; on any error the edit is deleted.
"""

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/"
KEY_ENV = "PLAY_SERVICE_ACCOUNT_KEY"
NOTES_LANGUAGE = "en-US"
NOTES_MAX = 500  # Play's limit per language


class ApiError(Exception):
    pass


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def access_token(key_file):
    """Exchanges a signed JWT for an OAuth access token (service account flow)."""
    with open(key_file) as f:
        key = json.load(f)
    if key.get("type") != "service_account":
        sys.exit("%s is not a service account key (JSON)" % key_file)
    token_uri = key.get("token_uri", "https://oauth2.googleapis.com/token")
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64url(
        json.dumps(
            {
                "iss": key["client_email"],
                "scope": SCOPE,
                "aud": token_uri,
                "iat": now,
                "exp": now + 3600,
            }
        ).encode()
    )
    signing_input = ("%s.%s" % (header, claims)).encode()
    # openssl needs the private key in a file; keep it in a private temporary directory
    # only as long as the signing takes.
    with tempfile.TemporaryDirectory() as tmp:
        pem = os.path.join(tmp, "key.pem")
        fd = os.open(pem, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as f:
            f.write(key["private_key"])
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", pem],
            input=signing_input,
            capture_output=True,
            check=True,
        ).stdout
    assertion = "%s.%s" % (signing_input.decode(), b64url(signature))
    body = urllib.parse.urlencode(
        {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion}
    ).encode()
    return request("POST", token_uri, body=body, content_type="application/x-www-form-urlencoded")[
        "access_token"
    ]


def request(method, url, body=None, content_type="application/json", token=None):
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            data = resp.read()
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        try:
            detail = json.loads(detail)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            pass
        raise ApiError("%s %s: HTTP %d: %s" % (method, url.split("?")[0], e.code, detail))
    return json.loads(data) if data else {}


class Edit:
    """A Play edit: changes that take effect together when committed."""

    def __init__(self, token, package):
        self.token = token
        self.package = package
        self.base = API + urllib.parse.quote(package) + "/edits"
        self.id = None

    def __enter__(self):
        self.id = self.call("POST", "")["id"]
        return self

    def __exit__(self, exc_type, exc, tb):
        if self.id is not None:  # not committed
            try:
                self.call("DELETE", "")
            except ApiError as e:
                print("Could not delete the edit: %s" % e, file=sys.stderr)

    def call(self, method, path, payload=None, query=""):
        url = self.base + ("/" + self.id if self.id else "") + path + query
        body = json.dumps(payload).encode() if payload is not None else None
        return request(method, url, body=body, token=self.token)

    def upload_bundle(self, path):
        url = "%s%s/edits/%s/bundles?uploadType=media" % (
            UPLOAD_API,
            urllib.parse.quote(self.package),
            self.id,
        )
        with open(path, "rb") as f:
            data = f.read()
        return request(
            "POST", url, body=data, content_type="application/octet-stream", token=self.token
        )

    def commit(self, send_for_review):
        query = "" if send_for_review else "?changesNotSentForReview=true"
        result = self.call("POST", ":commit", query=query)
        self.id = None
        return result


def track_path(track):
    return "/tracks/" + urllib.parse.quote(track, safe="")


def print_tracks(edit):
    tracks = edit.call("GET", "/tracks").get("tracks", [])
    for t in sorted(tracks, key=lambda t: t["track"]):
        releases = t.get("releases", [])
        if not releases:
            continue
        print(t["track"])
        for r in releases:
            print(
                "  %-10s %-12s %s"
                % (r.get("status", "?"), ",".join(r.get("versionCodes", [])), r.get("name", ""))
            )


def check_signed(bundle):
    """Play only accepts bundles signed with the upload key; catch an unsigned one early."""
    with zipfile.ZipFile(bundle) as z:
        names = z.namelist()
    if not any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC")) for n in names):
        sys.exit("%s is not signed. Sign it first: bazel run //watchface:release" % bundle)


def main():
    p = argparse.ArgumentParser(
        prog="bazel run //watchface:upload --",
        description="Upload the signed app bundle to Google Play and release it on a track.",
        epilog="The service account key is a JSON file from Google Cloud; keep it out of the "
        "repository. The service account needs release permissions for the app in Play "
        "Console (Users and permissions). See SPEC.md, Releasing.",
    )
    p.add_argument("--package", required=True, help=argparse.SUPPRESS)
    p.add_argument("--version-code", type=int, required=True, help=argparse.SUPPRESS)
    p.add_argument("--version-name", required=True, help=argparse.SUPPRESS)
    p.add_argument(
        "--key",
        default=os.environ.get(KEY_ENV),
        help="service account key (JSON); default: $%s" % KEY_ENV,
    )
    p.add_argument("--bundle", help="signed bundle; default: what //watchface:release writes")
    p.add_argument(
        "--track",
        default="wear:production",
        help="Play track, such as wear:production, wear:beta or wear:qa (default: %(default)s)",
    )
    p.add_argument(
        "--status",
        choices=["completed", "draft"],
        default="completed",
        help="completed: release to everyone once Google's review passes; draft: leave a draft "
        "release to finish in Play Console (default: %(default)s)",
    )
    p.add_argument(
        "--notes",
        help="release notes (%s), a text file; default: playstore/release-notes/<version name>.txt"
        " if it exists" % NOTES_LANGUAGE,
    )
    p.add_argument(
        "--no-review",
        action="store_true",
        help="commit without sending the changes for review (for apps with managed publishing)",
    )
    p.add_argument(
        "--list-tracks",
        action="store_true",
        help="only show the releases on each track, and change nothing",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="check the bundle, notes and key file and show what would be uploaded, offline",
    )
    args = p.parse_args()

    workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY", ".")
    cwd = os.environ.get("BUILD_WORKING_DIRECTORY", ".")

    def path(arg):
        return os.path.join(cwd, os.path.expanduser(arg))

    if not args.key:
        p.error("give --key or set $%s" % KEY_ENV)
    key = path(args.key)
    if not os.path.isfile(key):
        sys.exit("No such key file: %s" % key)

    if args.list_tracks:
        with Edit(access_token(key), args.package) as edit:
            print_tracks(edit)
        return

    bundle = path(args.bundle) if args.bundle else os.path.join(
        workspace, "dist", "%s-%s-%d.aab" % (args.package, args.version_name, args.version_code)
    )
    if not os.path.isfile(bundle):
        sys.exit("No such bundle: %s\nSign one first: bazel run //watchface:release" % bundle)
    check_signed(bundle)

    notes_file = path(args.notes) if args.notes else os.path.join(
        workspace, "playstore", "release-notes", args.version_name + ".txt"
    )
    notes = None
    if os.path.isfile(notes_file):
        with open(notes_file) as f:
            notes = f.read().strip()
        if len(notes) > NOTES_MAX:
            sys.exit("%s: %d characters, Play allows %d" % (notes_file, len(notes), NOTES_MAX))
    elif args.notes:
        sys.exit("No such release notes file: %s" % notes_file)

    release = {
        "name": "%s (%d)" % (args.version_name, args.version_code),
        "versionCodes": [str(args.version_code)],
        "status": args.status,
    }
    if notes:
        release["releaseNotes"] = [{"language": NOTES_LANGUAGE, "text": notes}]

    print("Package:  %s" % args.package)
    print("Bundle:   %s" % bundle)
    print("Release:  %s on %s, %s" % (release["name"], args.track, args.status))
    print("Notes:    %s" % (notes_file if notes else "none"))
    if notes:
        print("".join("          " + line + "\n" for line in notes.splitlines()), end="")
    if args.dry_run:
        print("Dry run: nothing uploaded.")
        return

    with Edit(access_token(key), args.package) as edit:
        uploaded = {b["versionCode"] for b in edit.call("GET", "/bundles").get("bundles", [])}
        if args.version_code in uploaded:
            sys.exit(
                "Play already has version code %d. Raise VERSION_CODE in watchface/version.bzl, "
                "then build and sign again." % args.version_code
            )
        print("Uploading...")
        result = edit.upload_bundle(bundle)
        if result.get("versionCode") != args.version_code:
            sys.exit(
                "The bundle has version code %s, not %d: it was signed from another build."
                % (result.get("versionCode"), args.version_code)
            )
        edit.call("PUT", track_path(args.track), {"track": args.track, "releases": [release]})
        edit.commit(send_for_review=not args.no_review)
    print("Committed: %s on %s (%s)." % (release["name"], args.track, args.status))
    if args.status == "completed" and not args.no_review:
        print("It goes live once Google's review passes; Play Console shows its progress.")


if __name__ == "__main__":
    try:
        main()
    except ApiError as e:
        sys.exit(str(e))
