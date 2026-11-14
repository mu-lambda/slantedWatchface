"""Validates a watchface.xml against the official Watch Face Format XSD (XSD 1.1).

Usage: validate_schema <watchface.xsd> <watchface.xml>
"""

import sys

import xmlschema


def main():
    schema_path, xml_path = sys.argv[1:]
    schema = xmlschema.XMLSchema11(schema_path)
    errors = list(schema.iter_errors(xml_path))
    for error in errors[:20]:
        print("%s\n  at %s\n" % (error.reason, error.path), file=sys.stderr)
    if errors:
        print("%d schema error(s) in %s" % (len(errors), xml_path), file=sys.stderr)
        sys.exit(1)
    print("%s is valid" % xml_path)


if __name__ == "__main__":
    main()
