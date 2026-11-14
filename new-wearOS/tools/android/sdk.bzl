"""Android SDK pieces for Bazel.

- @android_build_tools: a pinned build-tools release (aapt2, zipalign, apksigner),
  downloaded from Google's SDK repository and checked against a known hash.
- @local_android_sdk: what has to come from this machine: android.jar of the newest
  installed platform, adb, and the debug keystore. The SDK is found via
  $ANDROID_HOME, $ANDROID_SDK_ROOT, or the default install location.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# build-tools 36.1.0, from https://dl.google.com/android/repository/repository2-3.xml
# (sha1 cross-checked against the listing).
_BUILD_TOOLS = {
    "linux": (
        "https://dl.google.com/android/repository/build-tools_r36.1_linux.zip",
        "a7b5889e4a79fcf3b0976bef40d401f4240fb1eed891d9d91169da1111e11d78",
    ),
    "macosx": (
        "https://dl.google.com/android/repository/build-tools_r36.1_macosx.zip",
        "cc56be1d4ea95041f32f4ebf3a4f5c6b990c7749d706852395006b5654eb82ff",
    ),
}

def _version_key(v):
    parts = []
    for p in v.replace("-", ".").split("."):
        parts.append(int(p) if p.isdigit() else -1)
    return parts

def _newest(rctx, path, prefix = ""):
    candidates = []
    for entry in rctx.path(path).readdir():
        name = entry.basename
        if name.startswith(prefix) and name[len(prefix):].replace(".", "").isdigit():
            candidates.append(name)
    if not candidates:
        fail("No %s* found in %s" % (prefix, path))
    return sorted(candidates, key = lambda n: _version_key(n[len(prefix):]))[-1]

def _local_android_sdk_impl(rctx):
    home = rctx.getenv("HOME", "")
    sdk = rctx.getenv("ANDROID_HOME") or rctx.getenv("ANDROID_SDK_ROOT")
    if not sdk:
        for candidate in [home + "/Library/Android/sdk", home + "/Android/Sdk"]:
            if rctx.path(candidate).exists:
                sdk = candidate
                break
    if not sdk:
        fail("Android SDK not found. Set ANDROID_HOME.")

    platform = sdk + "/platforms/" + _newest(rctx, sdk + "/platforms", "android-")
    rctx.symlink(platform + "/android.jar", "android.jar")
    rctx.symlink(sdk + "/platform-tools/adb", "adb")

    keystore = home + "/.android/debug.keystore"
    if not rctx.path(keystore).exists:
        fail("Debug keystore not found at %s (build any Android app once to create it)." % keystore)
    rctx.symlink(keystore, "debug.keystore")

    rctx.file("BUILD.bazel", """\
exports_files(
    ["android.jar", "adb", "debug.keystore"],
    visibility = ["//visibility:public"],
)
""")

local_android_sdk = repository_rule(
    implementation = _local_android_sdk_impl,
    local = True,
)

def _android_sdk_impl(mctx):
    host = "macosx" if mctx.os.name.startswith("mac") else "linux"
    url, sha256 = _BUILD_TOOLS[host]
    http_archive(
        name = "android_build_tools",
        url = url,
        sha256 = sha256,
        strip_prefix = "android-16",
        build_file_content = """exports_files(
    ["aapt2", "zipalign", "lib/apksigner.jar"],
    visibility = ["//visibility:public"],
)
""",
    )
    local_android_sdk(name = "local_android_sdk")
    return mctx.extension_metadata(reproducible = False)

android_sdk = module_extension(implementation = _android_sdk_impl)
