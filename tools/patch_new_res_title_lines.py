from __future__ import annotations

import copy
import struct
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class Tag:
    type_id: int
    value: Any
    list_type: int | None = None


class NbtReader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def unpack(self, fmt: str) -> Any:
        value = struct.unpack_from(fmt, self.data, self.offset)[0]
        self.offset += struct.calcsize(fmt)
        return value

    def string(self) -> str:
        length = self.unpack(">H")
        value = self.data[self.offset : self.offset + length].decode("utf-8")
        self.offset += length
        return value

    def payload(self, type_id: int) -> Tag:
        if type_id == 1:
            return Tag(type_id, self.unpack(">b"))
        if type_id == 2:
            return Tag(type_id, self.unpack(">h"))
        if type_id == 3:
            return Tag(type_id, self.unpack(">i"))
        if type_id == 4:
            return Tag(type_id, self.unpack(">q"))
        if type_id == 5:
            return Tag(type_id, self.unpack(">f"))
        if type_id == 6:
            return Tag(type_id, self.unpack(">d"))
        if type_id == 7:
            length = self.unpack(">i")
            value = self.data[self.offset : self.offset + length]
            self.offset += length
            return Tag(type_id, value)
        if type_id == 8:
            return Tag(type_id, self.string())
        if type_id == 9:
            list_type = self.unpack(">B")
            length = self.unpack(">i")
            return Tag(type_id, [self.payload(list_type) for _ in range(length)], list_type)
        if type_id == 10:
            values: OrderedDict[str, Tag] = OrderedDict()
            while True:
                child_type = self.unpack(">B")
                if child_type == 0:
                    return Tag(type_id, values)
                child_name = self.string()
                values[child_name] = self.payload(child_type)
        if type_id == 11:
            length = self.unpack(">i")
            return Tag(type_id, [self.unpack(">i") for _ in range(length)])
        if type_id == 12:
            length = self.unpack(">i")
            return Tag(type_id, [self.unpack(">q") for _ in range(length)])
        raise ValueError(f"Unsupported NBT tag type: {type_id}")

    def read(self) -> tuple[str, Tag]:
        type_id = self.unpack(">B")
        name = self.string()
        root = self.payload(type_id)
        if self.offset != len(self.data):
            raise ValueError(f"Trailing NBT bytes: {len(self.data) - self.offset}")
        return name, root


class NbtWriter:
    def __init__(self):
        self.parts: list[bytes] = []

    def pack(self, fmt: str, value: Any) -> None:
        self.parts.append(struct.pack(fmt, value))

    def string(self, value: str) -> None:
        encoded = value.encode("utf-8")
        self.pack(">H", len(encoded))
        self.parts.append(encoded)

    def payload(self, tag: Tag) -> None:
        type_id = tag.type_id
        if type_id == 1:
            self.pack(">b", tag.value)
        elif type_id == 2:
            self.pack(">h", tag.value)
        elif type_id == 3:
            self.pack(">i", tag.value)
        elif type_id == 4:
            self.pack(">q", tag.value)
        elif type_id == 5:
            self.pack(">f", tag.value)
        elif type_id == 6:
            self.pack(">d", tag.value)
        elif type_id == 7:
            self.pack(">i", len(tag.value))
            self.parts.append(bytes(tag.value))
        elif type_id == 8:
            self.string(tag.value)
        elif type_id == 9:
            self.pack(">B", tag.list_type)
            self.pack(">i", len(tag.value))
            for child in tag.value:
                self.payload(child)
        elif type_id == 10:
            for name, child in tag.value.items():
                self.pack(">B", child.type_id)
                self.string(name)
                self.payload(child)
            self.pack(">B", 0)
        elif type_id == 11:
            self.pack(">i", len(tag.value))
            for value in tag.value:
                self.pack(">i", value)
        elif type_id == 12:
            self.pack(">i", len(tag.value))
            for value in tag.value:
                self.pack(">q", value)
        else:
            raise ValueError(f"Unsupported NBT tag type: {type_id}")

    def write(self, name: str, root: Tag) -> bytes:
        self.pack(">B", root.type_id)
        self.string(name)
        self.payload(root)
        return b"".join(self.parts)


def compound(**values: Tag) -> Tag:
    return Tag(10, OrderedDict(values))


def string(value: str) -> Tag:
    return Tag(8, value)


def integer(value: int) -> Tag:
    if value > 0x7FFFFFFF:
        value -= 0x100000000
    return Tag(3, value)


def floating(value: float) -> Tag:
    return Tag(5, value)


def percent(value: float) -> Tag:
    return compound(type=string("PERCENT"), value=floating(value))


def length(value: float) -> Tag:
    return compound(type=string("LENGTH"), value=floating(value))


def children(node: Tag) -> list[Tag]:
    if node.type_id != 10:
        return []
    data = node.value.get("data")
    owner = data.value if data is not None and data.type_id == 10 else node.value
    child_list = owner.get("children")
    if child_list is None or child_list.type_id != 9:
        return []
    return child_list.value


def find_by_id(node: Tag, target_id: str) -> Tag | None:
    if node.type_id == 10:
        data = node.value.get("data")
        if data is not None and data.type_id == 10:
            node_id = data.value.get("id")
            if node_id is not None and node_id.value == target_id:
                return node
        descendants = node.value.values()
    elif node.type_id == 9:
        descendants = node.value
    else:
        descendants = ()
    for child in descendants:
        found = find_by_id(child, target_id)
        if found is not None:
            return found
    return None


def convert_button_to_text(root: Tag, target_id: str) -> None:
    node = find_by_id(root, target_id)
    if node is None:
        raise KeyError(f"Missing UI element: {target_id}")
    node_type = node.value.get("type")
    if node_type is not None and node_type.value == "text":
        return
    data = node.value["data"].value
    internal = data.pop("internal", None)
    if internal is None or not internal.value:
        raise ValueError(f"Button has no text payload: {target_id}")
    label = internal.value[0].value.get("text")
    if label is None:
        raise ValueError(f"Button has no label component: {target_id}")
    data["text"] = label
    node.value["type"] = string("text")


def divider(divider_id: str, left: float, top: float, width: float) -> Tag:
    color = 0x66D8CDE6
    return compound(
        data=compound(
            inline=compound(
                left=percent(left),
                top=percent(top),
                width=percent(width),
                position=string("ABSOLUTE"),
                height=length(1.0),
                background=compound(
                    data=compound(color=integer(color)),
                    type=string("color_rect_texture"),
                ),
            ),
            id=string(divider_id),
        ),
        index=integer(0),
        type=string("element"),
    )


def text_element(element_id: str, label: str, left: float, top: float, width: float, height: float) -> Tag:
    return compound(
        data=compound(
            inline=compound(
                left=percent(left),
                top=percent(top),
                width=percent(width),
                position=string("ABSOLUTE"),
                height=percent(height),
            ),
            id=string(element_id),
            text=compound(text=string(label)),
        ),
        index=integer(0),
        type=string("text"),
    )


def button_element(element_id: str, label: str, left: float, top: float, width: float, height: float) -> Tag:
    label_data = compound(text=compound(text=string(label)))
    node = compound(
        data=compound(
            internal=Tag(9, [label_data], 10),
            inline=compound(
                left=percent(left),
                top=percent(top),
                width=percent(width),
                position=string("ABSOLUTE"),
                height=percent(height),
                base_background=compound(
                    data=compound(color=integer(0xAA101010)),
                    type=string("color_rect_texture"),
                ),
                hover_background=compound(
                    data=compound(color=integer(0xCC383838)),
                    type=string("color_rect_texture"),
                ),
            ),
            id=string(element_id),
        ),
        index=integer(0),
        type=string("button"),
    )
    inline = node.value["data"].value["inline"].value
    inline["base-background"] = inline.pop("base_background")
    inline["hover-background"] = inline.pop("hover_background")
    return node


def option_tray() -> Tag:
    tray_children = [
        text_element("option_tray_title", "技能选项", 0.03, 0.06, 0.94, 0.16),
        text_element("option_tray_description", "选择一个选项以装备。", 0.03, 0.82, 0.94, 0.12),
    ]
    for index in range(4):
        tray_children.append(
            button_element(
                f"option_choice_{index}",
                f"选项 {index + 1}",
                0.03 + index * 0.24,
                0.27,
                0.21,
                0.47,
            )
        )
    for index, child in enumerate(tray_children):
        child.value["index"] = integer(index)
    tray = compound(
        data=compound(
            inline=compound(
                left=percent(0.37),
                top=percent(0.35),
                width=percent(0.2354),
                position=string("ABSOLUTE"),
                height=percent(0.16),
                background=compound(
                    data=compound(color=integer(0xB8000000)),
                    type=string("color_rect_texture"),
                ),
                overlay=compound(
                    data=compound(border=integer(1), color=integer(0x668E8A94)),
                    type=string("color_border_texture"),
                ),
                z_index=integer(50),
            ),
            children=Tag(9, tray_children, 10),
            id=string("option_tray"),
        ),
        index=integer(0),
        type=string("element"),
    )
    inline = tray.value["data"].value["inline"].value
    inline["z-index"] = inline.pop("z_index")
    return tray


def assign_button_ids(template: Tag) -> None:
    positions = {
        (0.10, 0.24): "super_button",
        (0.37, 0.27): "ability_slot_0",
        (0.44, 0.27): "ability_slot_1",
        (0.51, 0.27): "ability_slot_2",
        (0.58, 0.27): "ability_slot_3",
        (0.76, 0.27): "aspect_slot_button_0",
        (0.83, 0.27): "aspect_slot_button_1",
    }
    for node in children(template):
        if node.value.get("type") is None or node.value["type"].value != "button":
            continue
        data = node.value["data"].value
        inline = data.get("inline")
        if inline is None:
            continue
        left = inline.value.get("left")
        top = inline.value.get("top")
        if left is None or top is None:
            continue
        key = (round(left.value["value"].value, 2), round(top.value["value"].value, 2))
        element_id = positions.get(key)
        if element_id is not None:
            data["id"] = string(element_id)


def add_divider(parent: Tag, element: Tag) -> None:
    element_id = element.value["data"].value["id"].value
    existing = find_by_id(parent, element_id)
    if existing is not None:
        existing_index = existing.value.get("index", integer(0))
        existing.value = copy.deepcopy(element.value)
        existing.value["index"] = existing_index
        return
    sibling_list = children(parent)
    element.value["index"] = integer(len(sibling_list))
    sibling_list.append(element)


def read_nbt(path: Path) -> tuple[str, Tag]:
    return NbtReader(path.read_bytes()).read()


def main() -> None:
    resource_dir = Path("run/ldlib2/assets/destiny2-mod/resources")
    target = resource_dir / "new_res.ui.nbt"
    source = resource_dir / "aspect_screen.ui.nbt"
    root_name, root = read_nbt(target)
    _, source_root = read_nbt(source)

    for element_id in (
        "subclass_title",
        "subclass_subtitle",
        "super_label",
        "ability_label",
        "aspect_label",
        "fragment_title",
        "fragment_live_capacity",
        "fragment_description",
        "footer_hint",
    ):
        convert_button_to_text(root, element_id)

    fragment_panel = find_by_id(root, "fragment_panel")
    source_panel = find_by_id(source_root, "fragment_panel")
    if fragment_panel is None or source_panel is None:
        raise KeyError("fragment_panel is missing")
    target_inline = fragment_panel.value["data"].value["inline"].value
    source_inline = source_panel.value["data"].value["inline"].value
    target_inline["background"] = compound(
        data=compound(color=integer(0x55000000)),
        type=string("color_rect_texture"),
    )
    target_inline["overlay"] = copy.deepcopy(source_inline["overlay"])
    # Keep the element fully opaque and put transparency in the background
    # color itself, otherwise LDLib also fades all child controls and labels.
    target_inline["opacity"] = floating(1.0)
    fragment_panel.value["data"].value.pop("classes", None)

    # Section headings follow the exact left and right edges of their button rows.
    aspect_label = find_by_id(root, "aspect_label")
    if aspect_label is None:
        raise KeyError("aspect_label is missing")
    aspect_label.value["data"].value["inline"].value["left"] = percent(0.76)

    template = root.value["data"].value["template"]
    assign_button_ids(template)
    add_divider(template, divider("super_divider", 0.10, 0.448, 0.14))
    add_divider(template, divider("ability_divider", 0.37, 0.24, 0.2354))
    add_divider(template, divider("aspect_divider", 0.76, 0.24, 0.097))
    add_divider(fragment_panel, divider("fragment_divider", 0.04, 0.20, 0.92))
    add_divider(template, option_tray())

    patched = NbtWriter().write(root_name, root)
    # Parse the generated bytes before replacing the creator-facing template.
    NbtReader(patched).read()
    backup = target.with_name(target.name + ".before-title-lines")
    if not backup.exists():
        backup.write_bytes(target.read_bytes())
    target.write_bytes(patched)
    print(f"Patched {target} ({len(patched)} bytes)")


if __name__ == "__main__":
    main()
