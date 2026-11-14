"""Rules for packaging Watch Face Format (WFF) watch faces.

A WFF watch face is a resource-only APK (android:hasCode="false"), so instead of
the general-purpose Android rules (which always add a classes.dex) these rules
drive aapt2, zipalign, apksigner and bundletool directly.
"""

_SDK = "@local_android_sdk//:"
_BUILD_TOOLS = "@android_build_tools//:"
_BUNDLETOOL = "@bundletool//file:bundletool.jar"

WffResourcesInfo = provider(
    doc = "Resources produced by a rule, for wff_apk's resource_sets.",
    fields = {"resources": "dict: path under res/ (e.g. 'raw/watchface.xml') -> File"},
)

def _wff_apk_impl(ctx):
    name = ctx.label.name

    resources = {}
    for target, res_path in ctx.attr.resources.items():
        files = target.files.to_list()
        if len(files) != 1:
            fail("%s must be exactly one file, got %d" % (target.label, len(files)))
        resources[res_path] = files[0]
    for target in ctx.attr.resource_sets:
        for res_path, file in target[WffResourcesInfo].resources.items():
            if res_path in resources:
                fail("%s: resource %s is given twice" % (target.label, res_path))
            resources[res_path] = file

    # Stage every resource at its res/ path so that aapt2 can compile the directory.
    res_root = name + "_res"
    staged = []
    for res_path, file in resources.items():
        out = ctx.actions.declare_file(res_root + "/" + res_path)
        ctx.actions.symlink(output = out, target_file = file)
        staged.append(out)
    res_dir = ctx.bin_dir.path + "/" + ctx.label.package + "/" + res_root

    compiled = ctx.actions.declare_file(name + "_compiled.zip")
    ctx.actions.run(
        executable = ctx.file._aapt2,
        arguments = ["compile", "--dir", res_dir, "-o", compiled.path],
        inputs = staged,
        outputs = [compiled],
        mnemonic = "Aapt2Compile",
        progress_message = "Compiling resources for %{label}",
    )

    def link(out, proto):
        ctx.actions.run(
            executable = ctx.file._aapt2,
            arguments = [
                "link",
                "-o",
                out.path,
                "-I",
                ctx.file._android_jar.path,
                "--manifest",
                ctx.file.manifest.path,
                "--min-sdk-version",
                str(ctx.attr.min_sdk),
                "--target-sdk-version",
                str(ctx.attr.target_sdk),
                "--version-code",
                str(ctx.attr.version_code),
                "--version-name",
                ctx.attr.version_name,
                compiled.path,
            ] + (["--proto-format"] if proto else []),
            inputs = [compiled, ctx.file.manifest, ctx.file._android_jar],
            outputs = [out],
            mnemonic = "Aapt2Link",
            progress_message = "Linking %{label}" + (" for a bundle" if proto else ""),
        )

    unsigned = ctx.actions.declare_file(name + "_unsigned.apk")
    link(unsigned, proto = False)

    # The app bundle (what Google Play takes): the same resources linked in protobuf
    # format, rearranged into a base module, then packed by bundletool. Unsigned; see
    # wff_release.
    proto_apk = ctx.actions.declare_file(name + "_proto.apk")
    link(proto_apk, proto = True)
    module = ctx.actions.declare_file(name + "_base.zip")
    ctx.actions.run(
        executable = ctx.executable._bundle_module,
        arguments = [proto_apk.path, module.path],
        inputs = [proto_apk],
        outputs = [module],
        mnemonic = "BundleModule",
    )
    bundle = ctx.actions.declare_file(name + ".aab")
    ctx.actions.run(
        executable = "java",
        arguments = [
            "-jar",
            ctx.file._bundletool.path,
            "build-bundle",
            "--modules=" + module.path,
            "--output=" + bundle.path,
        ],
        inputs = [module, ctx.file._bundletool],
        outputs = [bundle],
        mnemonic = "BuildBundle",
        progress_message = "Building app bundle %{label}",
        use_default_shell_env = True,
    )

    aligned = ctx.actions.declare_file(name + "_aligned.apk")
    ctx.actions.run(
        executable = ctx.file._zipalign,
        arguments = ["-p", "-f", "4", unsigned.path, aligned.path],
        inputs = [unsigned],
        outputs = [aligned],
        mnemonic = "Zipalign",
    )

    apk = ctx.actions.declare_file(name + ".apk")
    ctx.actions.run(
        executable = "java",
        arguments = [
            "-jar",
            ctx.file._apksigner.path,
            "sign",
            "--ks",
            ctx.file.keystore.path,
            "--ks-pass",
            "pass:" + ctx.attr.keystore_password,
            "--ks-key-alias",
            ctx.attr.key_alias,
            "--v4-signing-enabled",
            "false",
            "--out",
            apk.path,
            aligned.path,
        ],
        inputs = [aligned, ctx.file.keystore, ctx.file._apksigner],
        outputs = [apk],
        mnemonic = "ApkSign",
        progress_message = "Signing %{label}",
        use_default_shell_env = True,
    )

    return [
        DefaultInfo(files = depset([apk])),
        OutputGroupInfo(bundle = depset([bundle])),
    ]

wff_apk = rule(
    implementation = _wff_apk_impl,
    doc = """Builds a signed, code-free APK from a manifest and resources.

The output group `bundle` holds the same app as an unsigned app bundle (.aab) for
Google Play; sign it with wff_release.""",
    attrs = {
        "manifest": attr.label(allow_single_file = [".xml"], mandatory = True),
        "resources": attr.label_keyed_string_dict(
            allow_files = True,
            doc = "Resource files, mapped to their path under res/ (e.g. 'raw/watchface.xml').",
        ),
        "resource_sets": attr.label_list(
            providers = [WffResourcesInfo],
            doc = "Targets providing further resources with their res/ paths.",
        ),
        "min_sdk": attr.int(default = 33),
        # Google Play requires Wear OS apps to target API 35 or later (since Aug 31, 2026).
        # Must match PLATFORM in sdk.bzl, whose android.jar the APK links against.
        "target_sdk": attr.int(default = 36),
        "version_code": attr.int(mandatory = True),
        "version_name": attr.string(mandatory = True),
        "keystore": attr.label(allow_single_file = True, default = _SDK + "debug.keystore"),
        "keystore_password": attr.string(default = "android"),
        "key_alias": attr.string(default = "androiddebugkey"),
        "_aapt2": attr.label(allow_single_file = True, cfg = "exec", default = _BUILD_TOOLS + "aapt2"),
        "_zipalign": attr.label(allow_single_file = True, cfg = "exec", default = _BUILD_TOOLS + "zipalign"),
        "_apksigner": attr.label(allow_single_file = True, cfg = "exec", default = _BUILD_TOOLS + "lib/apksigner.jar"),
        "_android_jar": attr.label(allow_single_file = True, default = _SDK + "android.jar"),
        "_bundletool": attr.label(allow_single_file = True, cfg = "exec", default = _BUNDLETOOL),
        "_bundle_module": attr.label(
            executable = True,
            cfg = "exec",
            default = "//tools/android:bundle_module",
        ),
    },
)

def res_files(srcs, prefix = "res/"):
    """Maps resource files to their path under res/, based on where they live in the package."""
    result = {}
    for src in srcs:
        if not src.startswith(prefix):
            fail("%s is not under %s" % (src, prefix))
        result[src] = src[len(prefix):]
    return result

def _wff_install_impl(ctx):
    if bool(ctx.file.apk) == bool(ctx.file.bundle):
        fail("give exactly one of apk and bundle")
    if ctx.file.apk:
        install = '"$adb" install -r "%s"' % ctx.file.apk.short_path
        inputs = [ctx.file.apk]
    else:
        # As Google Play would: bundletool picks the APKs for the device and installs
        # them. They are signed with the debug key.
        install = """apks="${{TMPDIR:-/tmp}}/wff_install_$$.apks"
java -jar "{bundletool}" build-apks --bundle="{bundle}" --output="$apks" --overwrite \\
    --connected-device --adb="$adb" ${{ANDROID_SERIAL:+--device-id=$ANDROID_SERIAL}} \\
    --ks="{keystore}" --ks-pass=pass:android --ks-key-alias=androiddebugkey
java -jar "{bundletool}" install-apks --apks="$apks" --adb="$adb" \\
    ${{ANDROID_SERIAL:+--device-id=$ANDROID_SERIAL}}
rm -f "$apks"
echo "Installed from the bundle"
""".format(
            bundletool = ctx.file._bundletool.short_path,
            bundle = ctx.file.bundle.short_path,
            keystore = ctx.file._debug_keystore.short_path,
        )
        inputs = [ctx.file.bundle, ctx.file._bundletool, ctx.file._debug_keystore]
    script = ctx.actions.declare_file(ctx.label.name + ".sh")
    ctx.actions.write(
        output = script,
        is_executable = True,
        content = """#!/bin/sh
set -eu
case "${{1:-}}" in
-h | --help)
    cat <<'USAGE'
Usage: bazel run {label} [-- --help]

Installs {package} on the device in $ANDROID_SERIAL (or the only one
connected) and makes it the active watch face.
USAGE
    if [ -n "{help}" ]; then
        echo
        cat "{help}"
    fi
    exit 0
    ;;
"") ;;
*)
    echo "unknown argument: $1 (see --help)" >&2
    exit 2
    ;;
esac
adb="{adb}"
{install}
# Make it the active watch face (works on emulators and debuggable builds).
"$adb" shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \\
    --es operation set-watchface --es watchFaceId "{package}" >/dev/null
echo "Installed and selected {package}"
""".format(
            adb = ctx.file._adb.short_path,
            install = install,
            package = ctx.attr.package,
            label = "//%s:%s" % (ctx.label.package, ctx.label.name),
            help = ctx.file.help.short_path if ctx.file.help else "",
        ),
    )
    runfiles = ctx.runfiles(files = inputs + [ctx.file._adb] + ctx.files.help)
    return [DefaultInfo(executable = script, runfiles = runfiles)]

wff_install = rule(
    implementation = _wff_install_impl,
    doc = "`bazel run` target that installs a watch face via adb and selects it. " +
          "With `-- --help`, it prints its usage followed by the `help` file.",
    executable = True,
    attrs = {
        "apk": attr.label(allow_single_file = [".apk"]),
        "bundle": attr.label(
            allow_single_file = [".aab"],
            doc = "Instead of apk: an app bundle, installed through bundletool.",
        ),
        "package": attr.string(mandatory = True),
        "help": attr.label(allow_single_file = True, doc = "Text appended to --help."),
        "_adb": attr.label(allow_single_file = True, default = _SDK + "adb"),
        "_bundletool": attr.label(allow_single_file = True, default = _BUNDLETOOL),
        "_debug_keystore": attr.label(allow_single_file = True, default = _SDK + "debug.keystore"),
    },
)

def _wff_release_impl(ctx):
    script = ctx.actions.declare_file(ctx.label.name + ".sh")
    ctx.actions.write(
        output = script,
        is_executable = True,
        content = """#!/bin/sh
set -eu
usage() {{
    cat <<'USAGE'
Usage: bazel run {label} -- --keystore <file> --alias <key alias> [--out <file.aab>]

Signs the app bundle with your Google Play upload key and writes it to <file.aab>
(default: dist/{default_out} in the workspace). jarsigner asks for the passwords.
USAGE
}}
keystore= alias= out=
while [ $# -gt 0 ]; do
    case "$1" in
    --keystore) keystore=$2; shift 2 ;;
    --alias) alias=$2; shift 2 ;;
    --out) out=$2; shift 2 ;;
    -h | --help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
done
if [ -z "$keystore" ] || [ -z "$alias" ]; then usage >&2; exit 2; fi
# Relative paths are relative to where `bazel run` was started.
case "$keystore" in /*) ;; *) keystore="$BUILD_WORKING_DIRECTORY/$keystore" ;; esac
out=${{out:-$BUILD_WORKSPACE_DIRECTORY/dist/{default_out}}}
case "$out" in /*) ;; *) out="$BUILD_WORKING_DIRECTORY/$out" ;; esac
mkdir -p "$(dirname "$out")"
jarsigner -keystore "$keystore" -signedjar "$out" "{bundle}" "$alias"
jarsigner -verify "$out" >/dev/null
echo "Signed: $out"
""".format(
            label = "//%s:%s" % (ctx.label.package, ctx.label.name),
            bundle = ctx.file.bundle.short_path,
            default_out = "%s-%s.aab" % (ctx.attr.package, ctx.attr.version_name),
        ),
    )
    return [DefaultInfo(executable = script, runfiles = ctx.runfiles(files = [ctx.file.bundle]))]

wff_release = rule(
    implementation = _wff_release_impl,
    doc = "`bazel run` target that signs an app bundle with an upload key, for Google Play.",
    executable = True,
    attrs = {
        "bundle": attr.label(allow_single_file = [".aab"], mandatory = True),
        "package": attr.string(mandatory = True),
        "version_name": attr.string(mandatory = True, doc = "Only for the default file name."),
    },
)
