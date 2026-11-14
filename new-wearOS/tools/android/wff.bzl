"""Rules for packaging Watch Face Format (WFF) watch faces.

A WFF watch face is a resource-only APK (android:hasCode="false"), so instead of
the general-purpose Android rules (which always add a classes.dex) these rules
drive aapt2, zipalign and apksigner directly.
"""

_SDK = "@local_android_sdk//:"
_BUILD_TOOLS = "@android_build_tools//:"

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

    unsigned = ctx.actions.declare_file(name + "_unsigned.apk")
    ctx.actions.run(
        executable = ctx.file._aapt2,
        arguments = [
            "link",
            "-o",
            unsigned.path,
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
        ],
        inputs = [compiled, ctx.file.manifest, ctx.file._android_jar],
        outputs = [unsigned],
        mnemonic = "Aapt2Link",
        progress_message = "Linking %{label}",
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

    return [DefaultInfo(files = depset([apk]))]

wff_apk = rule(
    implementation = _wff_apk_impl,
    doc = "Builds a signed, code-free APK from a manifest and resources.",
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
        "target_sdk": attr.int(default = 34),
        "version_code": attr.int(mandatory = True),
        "version_name": attr.string(mandatory = True),
        "keystore": attr.label(allow_single_file = True, default = _SDK + "debug.keystore"),
        "keystore_password": attr.string(default = "android"),
        "key_alias": attr.string(default = "androiddebugkey"),
        "_aapt2": attr.label(allow_single_file = True, cfg = "exec", default = _BUILD_TOOLS + "aapt2"),
        "_zipalign": attr.label(allow_single_file = True, cfg = "exec", default = _BUILD_TOOLS + "zipalign"),
        "_apksigner": attr.label(allow_single_file = True, cfg = "exec", default = _BUILD_TOOLS + "lib/apksigner.jar"),
        "_android_jar": attr.label(allow_single_file = True, default = _SDK + "android.jar"),
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
    script = ctx.actions.declare_file(ctx.label.name + ".sh")
    ctx.actions.write(
        output = script,
        is_executable = True,
        content = """#!/bin/sh
set -eu
adb="{adb}"
"$adb" install -r "{apk}"
# Make it the active watch face (works on emulators and debuggable builds).
"$adb" shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \\
    --es operation set-watchface --es watchFaceId "{package}" >/dev/null
echo "Installed and selected {package}"
""".format(
            adb = ctx.file._adb.short_path,
            apk = ctx.file.apk.short_path,
            package = ctx.attr.package,
        ),
    )
    runfiles = ctx.runfiles(files = [ctx.file.apk, ctx.file._adb])
    return [DefaultInfo(executable = script, runfiles = runfiles)]

wff_install = rule(
    implementation = _wff_install_impl,
    doc = "`bazel run` target that installs a watch face via adb and selects it.",
    executable = True,
    attrs = {
        "apk": attr.label(allow_single_file = [".apk"], mandatory = True),
        "package": attr.string(mandatory = True),
        "_adb": attr.label(allow_single_file = True, default = _SDK + "adb"),
    },
)
