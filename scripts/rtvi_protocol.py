#!/usr/bin/env python3
#
# Copyright (c) 2026, Daily.
#
# SPDX-License-Identifier: BSD-2-Clause
#

"""Generates the RTVI protocol's Kotlin types from its AsyncAPI document.

Pipecat publishes an AsyncAPI document for each RTVI protocol version. The one
for the version `RTVI_PROTOCOL_VERSION` names, in `PipecatClient.kt`, is kept in
`spec/<version>/rtvi.asyncapi.json`, and this script writes
`pipecat-client-android/src/main/java/ai/pipecat/client/types/RTVIProtocol.kt`
from it.

    python3 scripts/rtvi_protocol.py sync [--ref <git ref> | --pipecat <path>]
        Copies the version's document from Pipecat, then generates: from the
        pipecat-ai/pipecat repository at a ref, `main` unless one is given, or
        from a local Pipecat checkout.
    python3 scripts/rtvi_protocol.py generate
        Writes the generated file from the kept document.
    python3 scripts/rtvi_protocol.py check
        Fails when the generated file isn't what the kept document produces.

It runs on the Python that ships with macOS, 3.9, with no dependencies.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import textwrap
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "pipecat-client-android" / "src" / "main" / "java" / "ai" / "pipecat" / "client"
OUTPUT = SOURCES / "types" / "RTVIProtocol.kt"
VERSION_FILE = SOURCES / "PipecatClient.kt"
PACKAGE = "ai.pipecat.client.types"
PIPECAT_SPEC = "src/pipecat/processors/frameworks/rtvi/spec"
SCHEMAS = "#/components/schemas/"
LINE_LENGTH = 100

# Messages the client doesn't handle or send, whose types aren't generated. The
# UI messages describe and drive the elements of a web page.
SKIPPED_MESSAGES = {
    "raw-audio",
    "raw-audio-batch",
    "ui-cancel-job-group",
    "ui-command",
    "ui-event",
    "ui-job-group",
    "ui-snapshot",
}

# Schemas whose public type is hand-written, by the type's name. Their fields
# can't follow the document's: they keep a type's fields optional, its
# constructor, or a fallback for older bots.
HAND_WRITTEN = {
    "BotOutputMessageData": "BotOutputData",
    "LLMFunctionCallMessageData": "LLMFunctionCallData",
    "LLMFunctionCallResultData": "LLMFunctionCallResult",
    "RawServerResponseData": "DataMessage",
    "SendTextOptions": "SendTextOptions",
    "UserTranscriptionMessageData": "Transcript",
}

# Public names that don't follow from the schema's, which lose a `MessageData`
# suffix to `Data`: the SDK's names from before its types were generated, and
# client-js's names.
TYPE_NAMES = {
    "File": "RTVIFile",
    "LLMFunctionCallStartMessageData": "LLMFunctionCallStartedData",
    "MetricsMessageData": "PipecatMetrics",
    "SpokenProgressData": "BotOutputProgressData",
    "TextMessageData": "TextMessageData",
    "TextType": "BotOutputType",
}

# Field names that don't follow from the property's, kept from the SDK's names.
FIELD_NAMES = {
    "tool_call_id": "toolCallID",
}

# Names of enums declared for a property, where its name isn't one: `Type` is
# also `ai.pipecat.client.types.Type`.
ENUM_NAMES = {
    "type": "SourceType",
}

# Types only client messages use that apps build too; the other client-message
# types are internal.
PUBLIC_CLIENT_TYPES = {"File", "FileBytes", "FileUrl", "KeypadEntry", "SendFileOptions"}

# Names of the keypad's keys, which aren't Kotlin identifiers.
KEY_NAMES = {
    "0": "Zero",
    "1": "One",
    "2": "Two",
    "3": "Three",
    "4": "Four",
    "5": "Five",
    "6": "Six",
    "7": "Seven",
    "8": "Eight",
    "9": "Nine",
    "#": "Pound",
    "*": "Star",
}

KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
    "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
    "true", "try", "typealias", "typeof", "val", "var", "when", "while",
}  # fmt: skip


class GenerationError(Exception):
    pass


def protocol_version() -> str:
    match = re.search(r'RTVI_PROTOCOL_VERSION = "([^"]+)"', VERSION_FILE.read_text())
    if not match:
        raise GenerationError(f"RTVI_PROTOCOL_VERSION not found in {VERSION_FILE.relative_to(ROOT)}")
    return match.group(1)


def document_path(version: str) -> Path:
    return ROOT / "spec" / version / "rtvi.asyncapi.json"


def camel(name: str, upper: bool = False) -> str:
    words = [word for word in re.split(r"[^A-Za-z0-9]+", name) if word]
    result = "".join(word[:1].upper() + word[1:] for word in words)
    return result if upper else result[:1].lower() + result[1:]


def identifier(name: str) -> str:
    return f"`{name}`" if name in KEYWORDS else name


def string_literal(text: str) -> str:
    return json.dumps(text, ensure_ascii=False).replace("$", "\\$")


def ref_name(schema: dict) -> str | None:
    """The schema a `$ref`, or an `allOf` holding only one, points at."""
    if "$ref" in schema:
        return schema["$ref"].removeprefix(SCHEMAS)
    if len(schema.get("allOf", [])) == 1:
        return ref_name(schema["allOf"][0])
    return None


def allows_anything(schema: dict) -> bool:
    constraints = ("type", "$ref", "anyOf", "allOf", "oneOf", "const", "enum", "properties", "items")
    return not any(keyword in schema for keyword in constraints)


def indented(lines: list[str], indent: str = "    ") -> list[str]:
    return [indent + line if line else "" for line in lines]


class Generator:
    def __init__(self, document: dict):
        self.document = document
        self.schemas = document["components"]["schemas"]
        self.server_schemas = self._reachable("sendServerMessages")
        self.client_schemas = self._reachable("receiveClientMessages") - self.server_schemas
        # The sealed interfaces each type implements, as a member of a union.
        self.supertypes: dict[str, list[str]] = {}
        # Serializers of the unions, declared after the types.
        self.serializers: list[list[str]] = []
        self.imports: set[str] = set()

    def _reachable(self, operation: str) -> set[str]:
        """The schemas the operation's messages use, except the skipped messages'."""
        found: set[str] = set()

        def walk(value):
            if isinstance(value, dict):
                name = value.get("$ref", "").removeprefix(SCHEMAS)
                if name and name in self.schemas and name not in found:
                    found.add(name)
                    walk(self.schemas[name])
                for item in value.values():
                    walk(item)
            elif isinstance(value, list):
                for item in value:
                    walk(item)

        messages = self.document["components"]["messages"]
        for ref in self.document["operations"][operation]["messages"]:
            message = messages[ref["$ref"].split("/")[-1]]
            if message["name"] not in SKIPPED_MESSAGES:
                walk(message["payload"])
        return found

    def type_name(self, schema_name: str) -> str:
        if schema_name in HAND_WRITTEN:
            return HAND_WRITTEN[schema_name]
        if schema_name in TYPE_NAMES:
            return TYPE_NAMES[schema_name]
        return re.sub(r"MessageData$", "Data", schema_name)

    def internal(self, schema_name: str) -> bool:
        return schema_name not in self.server_schemas and schema_name not in PUBLIC_CLIENT_TYPES

    # Kotlin types

    def kotlin_type(self, schema: dict, owner: str, field: str, nested: list[list[str]]) -> tuple[str, bool]:
        """A property's Kotlin type, and whether the schema allows null.

        A type the property needs of its own, such as an enum, is added to
        `nested`, to be declared inside its owner's.
        """
        name = ref_name(schema)
        if name:
            return self.type_name(name), False
        if "anyOf" in schema:
            options = [option for option in schema["anyOf"] if option.get("type") != "null"]
            nullable = len(options) < len(schema["anyOf"])
            if len(options) == 1:
                kotlin, _ = self.kotlin_type(options[0], owner, field, nested)
                return kotlin, nullable
            if any(allows_anything(option) or option.get("type") == "object" for option in options):
                return "Value", nullable
            refs = [ref_name(option) for option in options]
            if all(refs):
                enums = [ref for ref in refs if "enum" in self.schemas[ref]]
                if not enums:
                    union = camel(field, upper=True)
                    nested.append(self.union(owner, union, [self.type_name(ref) for ref in refs]))
                    return union, nullable
            # An open enum takes the strings beside it.
            if all(option.get("type") == "string" or ref_name(option) for option in options):
                enums = [ref_name(o) for o in options if ref_name(o)]
                if len(enums) == 1 and "enum" in self.schemas[enums[0]]:
                    return self.type_name(enums[0]), nullable
            raise GenerationError(f"{owner}.{field}: unsupported anyOf {json.dumps(schema['anyOf'])}")
        if allows_anything(schema):
            return "Value", False
        if "enum" in schema:
            enum = ENUM_NAMES.get(field) or camel(field, upper=True)
            # The schema's description is the property's.
            nested.append(self.open_enum(enum, schema, described=False))
            return enum, False
        kind = schema.get("type")
        if kind == "string":
            return "String", False
        if kind == "integer":
            return "Int", False
        if kind == "number":
            return "Double", False
        if kind == "boolean":
            return "Boolean", False
        if kind == "array":
            if "items" not in schema:
                return "List<Value>", False
            item, _ = self.kotlin_type(schema["items"], owner, field, nested)
            return f"List<{item}>", False
        if kind == "object" and "properties" not in schema:
            return "Value", False
        raise GenerationError(f"{owner}.{field}: unsupported schema {json.dumps(schema)}")

    # Declarations

    def doc_comment(self, text: str | None, indent: str) -> list[str]:
        if not text:
            return []
        width = LINE_LENGTH - len(indent) - 3
        lines = []
        for paragraph in text.strip().split("\n"):
            wrapped = textwrap.wrap(paragraph, width, break_long_words=False, break_on_hyphens=False)
            lines += [f"{indent} * {line}" for line in wrapped] or [f"{indent} *"]
        if len(lines) == 1 and len(lines[0]) + 3 <= LINE_LENGTH:
            return [f"{indent}/** {lines[0].removeprefix(indent + ' * ')} */"]
        return [f"{indent}/**"] + lines + [f"{indent} */"]

    def open_enum(self, name: str, schema: dict, internal: bool = False, described: bool = True) -> list[str]:
        """An enum as a value class of its values, which also takes values it doesn't name."""
        lines = self.doc_comment(schema.get("description"), "") if described else []
        lines += [
            "@Serializable",
            "@JvmInline",
            f"{'internal ' if internal else ''}value class {name}(val value: String) {{",
            "    companion object {",
        ]
        for value in schema["enum"]:
            member = KEY_NAMES.get(value) or camel(value, upper=True)
            lines.append(f"        val {member} = {name}({string_literal(value)})")
        lines += ["    }", "}"]
        self.imports.add("kotlinx.serialization.Serializable")
        return lines

    def union(self, owner: str, name: str, cases: list[str]) -> list[str]:
        """A value of one of several types, decoded as the first that takes it."""
        for case in cases:
            self.supertypes.setdefault(case, []).append(f"{owner}.{name}")
        serializer = f"{owner}{name}Serializer"
        self.serializers.append(
            [
                f"internal object {serializer} : KSerializer<{owner}.{name}> {{",
                "    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor",
                "",
                f"    override fun deserialize(decoder: Decoder): {owner}.{name} {{",
                "        val input = decoder as JsonDecoder",
                "        val element = input.decodeJsonElement()",
            ]
            + [
                f"        runCatching {{ return input.json.decodeFromJsonElement({case}.serializer(), element) }}"
                for case in cases
            ]
            + [
                f"        throw SerializationException({string_literal('Not any of ' + ', '.join(cases))})",
                "    }",
                "",
                f"    override fun serialize(encoder: Encoder, value: {owner}.{name}) {{",
                "        val output = encoder as JsonEncoder",
                "        val element = when (value) {",
            ]
            + [f"            is {case} -> output.json.encodeToJsonElement({case}.serializer(), value)" for case in cases]
            + ["        }", "        output.encodeJsonElement(element)", "    }", "}"]
        )
        self.imports.update(
            {
                "kotlinx.serialization.KSerializer",
                "kotlinx.serialization.SerializationException",
                "kotlinx.serialization.descriptors.SerialDescriptor",
                "kotlinx.serialization.encoding.Decoder",
                "kotlinx.serialization.encoding.Encoder",
                "kotlinx.serialization.json.JsonDecoder",
                "kotlinx.serialization.json.JsonElement",
                "kotlinx.serialization.json.JsonEncoder",
            }
        )
        return [
            f"@Serializable(with = {serializer}::class)",
            f"sealed interface {name}",
        ]

    def data_class(self, schema_name: str) -> list[str]:
        schema = self.schemas[schema_name]
        name = self.type_name(schema_name)
        internal = self.internal(schema_name)
        if "enum" in schema:
            return self.open_enum(name, schema, internal)

        required = set(schema.get("required", []))
        nested: list[list[str]] = []
        fields = []
        for prop, prop_schema in schema.get("properties", {}).items():
            kotlin, nullable = self.kotlin_type(prop_schema, name, prop, nested)
            optional = prop not in required
            default = prop_schema.get("default")
            # An enum's default is the value it's sent with; other defaults are the server's.
            sent_default = "enum" in prop_schema and isinstance(default, str)
            fields.append(
                {
                    "key": prop,
                    "name": FIELD_NAMES.get(prop) or camel(prop),
                    "type": kotlin + ("?" if nullable or (optional and not sent_default) else ""),
                    "optional": optional,
                    "default": f"{kotlin}.{KEY_NAMES.get(default) or camel(default, upper=True)}"
                    if sent_default
                    else "null",
                    "sent_default": sent_default,
                    "description": prop_schema.get("description"),
                }
            )

        self.imports.add("kotlinx.serialization.Serializable")
        lines = self.doc_comment(schema.get("description"), "")
        lines.append("@Serializable")
        visibility = "internal " if internal else ""
        supertypes = self.supertypes.get(name, [])
        inherits = f" : {', '.join(supertypes)}" if supertypes else ""
        if not fields:
            lines.append(f"{visibility}class {name}{inherits}")
            return lines

        # Required parameters first, so the optional ones can be left out.
        lines.append(f"{visibility}data class {name}(")
        for field in [f for f in fields if not f["optional"]] + [f for f in fields if f["optional"]]:
            lines += self.doc_comment(field["description"], "    ")
            if field["name"] != field["key"]:
                self.imports.add("kotlinx.serialization.SerialName")
                lines.append(f"    @SerialName({string_literal(field['key'])})")
            if field["sent_default"]:
                self.imports.update({"kotlinx.serialization.EncodeDefault", "kotlinx.serialization.ExperimentalSerializationApi"})
                lines.append("    @EncodeDefault")
            default = f" = {field['default']}" if field["optional"] else ""
            lines.append(f"    val {identifier(field['name'])}: {field['type']}{default},")
        lines.append(f"){inherits}" + (" {" if nested else ""))
        if nested:
            lines += indented(nested[0])
            for declaration in nested[1:]:
                lines += [""] + indented(declaration)
            lines.append("}")
        return lines

    def generate(self, version: str) -> str:
        names = [
            name
            for name in self.schemas
            if (name in self.server_schemas or name in self.client_schemas) and name not in HAND_WRITTEN
        ]
        names.sort(key=self.type_name)
        # A union's members are declared to implement it, which the first pass finds.
        for name in names:
            self.data_class(name)
        self.serializers = []
        declarations = [self.data_class(name) for name in names] + self.serializers

        imports = sorted(self.imports)
        lines = [
            f"// Generated by scripts/rtvi_protocol.py from spec/{version}/rtvi.asyncapi.json.",
            "// Don't edit it: run `python3 scripts/rtvi_protocol.py generate`.",
        ]
        if "kotlinx.serialization.EncodeDefault" in imports:
            lines.append("@file:OptIn(ExperimentalSerializationApi::class)")
        lines += ["", f"package {PACKAGE}", ""] + [f"import {name}" for name in imports]
        for declaration in declarations:
            lines += [""] + declaration
        return "\n".join(lines) + "\n"


def generate() -> str:
    version = protocol_version()
    path = document_path(version)
    if not path.exists():
        raise GenerationError(f"{path.relative_to(ROOT)} not found: run `sync` first")
    return Generator(json.loads(path.read_text())).generate(version)


def sync(ref: str | None, pipecat: str | None):
    version = protocol_version()
    spec = f"{PIPECAT_SPEC}/{version}/rtvi.asyncapi.json"
    if pipecat:
        text = (Path(pipecat) / spec).read_text()
    else:
        url = f"https://raw.githubusercontent.com/pipecat-ai/pipecat/{ref or 'main'}/{spec}"
        try:
            with urllib.request.urlopen(url) as response:
                text = response.read().decode()
        except OSError as error:
            raise GenerationError(f"Couldn't fetch {url}: {error}") from error
    path = document_path(version)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    print(f"Copied the protocol {version} document to {path.relative_to(ROOT)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    sync_command = commands.add_parser("sync")
    source = sync_command.add_mutually_exclusive_group()
    source.add_argument("--ref")
    source.add_argument("--pipecat")
    commands.add_parser("generate")
    commands.add_parser("check")
    args = parser.parse_args()

    try:
        if args.command == "sync":
            sync(args.ref, args.pipecat)
        if args.command in ("sync", "generate"):
            OUTPUT.write_text(generate())
            print(f"Wrote {OUTPUT.relative_to(ROOT)}")
        elif args.command == "check":
            if not OUTPUT.exists() or OUTPUT.read_text() != generate():
                raise GenerationError(
                    f"{OUTPUT.relative_to(ROOT)} is out of date: run `python3 scripts/rtvi_protocol.py generate`"
                )
            print(f"{OUTPUT.relative_to(ROOT)} is up to date")
    except GenerationError as error:
        sys.exit(f"error: {error}")


if __name__ == "__main__":
    main()
