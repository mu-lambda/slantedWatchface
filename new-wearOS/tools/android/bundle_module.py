"""Turns an APK linked by `aapt2 link --proto-format` into a bundletool module zip.

  bundle_module <proto.apk> <base.zip>

A module keeps the manifest under manifest/ and everything else (resources.pb, res/) at
the same place. Entries get a fixed timestamp, so the output is reproducible.
"""

import sys
import zipfile


def main(src, dst):
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w") as zout:
        for info in sorted(zin.infolist(), key=lambda i: i.filename):
            name = info.filename
            if name == "AndroidManifest.xml":
                name = "manifest/" + name
            elif name != "resources.pb" and not name.startswith("res/"):
                raise SystemExit("unexpected entry in %s: %s" % (src, name))
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(entry, zin.read(info))


if __name__ == "__main__":
    main(*sys.argv[1:])
