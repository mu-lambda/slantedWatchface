"""Rule that runs the generator and hands its output to wff_apk as resources."""

load("//tools/android:wff.bzl", "WffResourcesInfo")

def _watchface_resources_impl(ctx):
    name = ctx.label.name
    watchface = ctx.actions.declare_file(name + "/watchface.xml")
    strings = ctx.actions.declare_file(name + "/generated_strings.xml")
    help = ctx.actions.declare_file(name + "/help.txt")
    resources = {
        "raw/watchface.xml": watchface,
        "values/generated_strings.xml": strings,
    }

    args = ctx.actions.args()
    args.add("--watchface_out", watchface)
    args.add("--strings_out", strings)
    args.add("--help_out", help)
    for font in ctx.files.fonts:
        # The file name is the font's resource name, which the generator's FONTS refers to.
        resource = font.basename.removesuffix(".ttf")
        icon = ctx.actions.declare_file("%s/font_icon_%s.xml" % (name, resource))
        args.add("--font", "%s=%s" % (resource, font.path))
        args.add("--icon_out", "%s=%s" % (resource, icon.path))
        resources["font/%s.ttf" % resource] = font
        resources["drawable/font_icon_%s.xml" % resource] = icon
    args.add("--defaults", ctx.var.get("WFF_DEFAULTS", ""))

    generated = [f for f in resources.values() if f not in ctx.files.fonts] + [help]
    ctx.actions.run(
        executable = ctx.attr._generator[DefaultInfo].files_to_run,
        arguments = [args],
        inputs = ctx.files.fonts,
        outputs = generated,
        mnemonic = "WffGenerate",
        progress_message = "Generating watch face %{label}",
    )
    return [
        DefaultInfo(files = depset(generated)),
        OutputGroupInfo(watchface_xml = depset([watchface]), help = depset([help])),
        WffResourcesInfo(resources = resources),
    ]

watchface_resources = rule(
    implementation = _watchface_resources_impl,
    doc = """Generates watchface.xml, its strings and the font icons.

Provides them, together with the fonts, as WffResourcesInfo for wff_apk. The output
group `watchface_xml` holds just watchface.xml, and `help` a text file that explains
WFF_DEFAULTS and lists every setting with its values (for wff_install's `help`).

`--define=WFF_DEFAULTS=id=value,...` overrides the default values of the user settings.
""",
    attrs = {
        "fonts": attr.label_list(
            allow_files = [".ttf"],
            mandatory = True,
            doc = "Font files named <resource>.ttf. Must match FONTS in generate.py.",
        ),
        "_generator": attr.label(
            default = "//watchface/generator:generate",
            executable = True,
            cfg = "exec",
        ),
    },
)
