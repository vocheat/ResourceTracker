"""Publish a reviewed description/gallery bundle without changing mod releases."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile


PROJECT = "QKduFlaB"
API = "https://api.modrinth.com/v2/project/" + PROJECT
USER_AGENT = "ResourceTracker page publisher (github.com/vocheat/ResourceTracker)"


def load_bundle(path):
    with zipfile.ZipFile(path) as archive:
        if sum(entry.file_size for entry in archive.infolist()) > 55 * 1024 * 1024:
            raise ValueError("Page bundle exceeds 55 MiB")
        manifest = json.loads(archive.read("gallery.json"))
        body = archive.read("description.md").decode("utf-8")
        gallery = manifest["gallery"]
        if manifest["project_id"] != PROJECT or not 7 <= len(gallery) <= 10:
            raise ValueError("Wrong project or gallery size")
        if not 1 <= len(manifest["summary"]) <= 255:
            raise ValueError("Invalid short description")
        files = [entry["file"] for entry in gallery]
        if len(set(files)) != len(files) or not all(re.fullmatch(r"[\w-]+\.png", f) for f in files):
            raise ValueError("Invalid or duplicate screenshot names")
        references = re.findall(r"!\[[^\]]*\]\(images/([^\s)]+)\)", body)
        if sorted(references) != sorted(files):
            raise ValueError("Description must use each selected screenshot once")
        images = {}
        for entry in gallery:
            data = archive.read("images/" + entry["file"])
            if len(data) > 5 * 1024 * 1024 or data[:8] != b"\x89PNG\r\n\x1a\n":
                raise ValueError("Invalid PNG: " + entry["file"])
            if struct.unpack(">II", data[16:24]) != (1920, 1080):
                raise ValueError("Screenshot must be 1920x1080: " + entry["file"])
            if hashlib.sha256(data).hexdigest() != entry["sha256"]:
                raise ValueError("Screenshot hash differs: " + entry["file"])
            if not entry["title_EN"] or not entry["caption_en"]:
                raise ValueError("Screenshot needs a title and caption")
            images[entry["file"]] = data
        old_urls = manifest["replace_gallery_urls"]
        if not all(url.startswith("https://cdn.modrinth.com/data/" + PROJECT + "/images/") for url in old_urls):
            raise ValueError("Old gallery URLs belong to another project")
    return manifest, body, images


def publish(manifest, body, images, output):
    token = os.environ.get("MODRINTH_TOKEN", "")
    if not token:
        raise RuntimeError("MODRINTH_TOKEN is missing")
    output.mkdir(parents=True, exist_ok=True)

    def save(name, value):
        (output / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")

    def request(method="GET", suffix="", payload=None, binary=None, query=None):
        if method == "GET":
            query = dict(query or {}, page_check=str(time.time_ns()))
        url = API + suffix + ("?" + urllib.parse.urlencode(query) if query else "")
        data = binary if binary is not None else (json.dumps(payload).encode() if payload is not None else None)
        headers = {"Authorization": token, "User-Agent": USER_AGENT, "Cache-Control": "no-cache"}
        if data is not None:
            headers["Content-Type"] = "image/png" if binary is not None else "application/json"
        req = urllib.request.Request(url, method=method, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"Modrinth {method} failed ({error.code}): " + error.read().decode()[:1000]) from None

    def raw_url(entry):
        return entry.get("raw_url") or entry["url"]

    def verify_image(entry, expected):
        url = raw_url(entry)
        if not url.startswith("https://cdn.modrinth.com/data/" + PROJECT + "/images/"):
            raise RuntimeError("Unexpected image host")
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": USER_AGENT}), timeout=120) as response:
            actual = response.read()
        if actual != expected:
            raise RuntimeError("Public image differs from the original Full HD screenshot")

    before = request()
    save("project-before.json", before)
    uploaded = {}
    for index, selected in enumerate(manifest["gallery"]):
        project = request()
        matches = [entry for entry in project["gallery"]
                   if entry.get("title") == selected["title_EN"] and entry.get("description") == selected["caption_en"]]
        if len(matches) > 1:
            raise RuntimeError("Ambiguous gallery entry: " + selected["title_EN"])
        if not matches:
            request("POST", "/gallery", binary=images[selected["file"]], query={
                "ext": "png", "featured": "true" if index == 0 else "false",
                "title": selected["title_EN"], "description": selected["caption_en"], "ordering": index})
            project = request()
            matches = [entry for entry in project["gallery"]
                       if entry.get("title") == selected["title_EN"] and entry.get("description") == selected["caption_en"]]
        if len(matches) != 1:
            raise RuntimeError("Uploaded screenshot is missing: " + selected["file"])
        entry = matches[0]
        verify_image(entry, images[selected["file"]])
        if entry.get("ordering") != index or entry.get("featured") != (index == 0):
            request("PATCH", "/gallery", query={"url": entry["url"], "ordering": index,
                    "featured": "true" if index == 0 else "false"})
        uploaded[selected["file"]] = entry
        save("uploaded.json", uploaded)
        print("Verified original screenshot:", selected["file"], flush=True)

    for filename, entry in uploaded.items():
        body = body.replace("(images/" + filename + ")", "(" + raw_url(entry) + ")")
    if "(images/" in body:
        raise RuntimeError("Description still contains local image links")
    request("PATCH", payload={"body": body, "description": manifest["summary"]})
    published = request()
    if published["body"] != body or published["description"] != manifest["summary"]:
        raise RuntimeError("Published description differs; old images retained")
    (output / "description-published.md").write_text(body, encoding="utf-8")

    # Delete only the explicitly reviewed previous gallery, after the new page is verified.
    new_urls = {entry["url"] for entry in uploaded.values()}
    for old_url in manifest["replace_gallery_urls"]:
        if old_url in new_urls or old_url in body:
            raise RuntimeError("Refusing to remove an image used by the new description")
        if any(entry["url"] == old_url for entry in published["gallery"]):
            request("DELETE", "/gallery", query={"url": old_url})
    final = request()
    save("project-published.json", final)
    if final["body"] != body or {entry["url"] for entry in final["gallery"]} != new_urls:
        raise RuntimeError("Final page or gallery differs from the reviewed selection")
    ordered = sorted(final["gallery"], key=lambda entry: entry["ordering"])
    for index, selected in enumerate(manifest["gallery"]):
        entry = ordered[index]
        if (entry["url"] != uploaded[selected["file"]]["url"] or entry["ordering"] != index
                or entry["featured"] != (index == 0) or entry["title"] != selected["title_EN"]
                or entry["description"] != selected["caption_en"]):
            raise RuntimeError("Final gallery order, feature, or caption differs")
    print(f"Published and verified {len(uploaded)} Full HD screenshots: https://modrinth.com/mod/resourcetracker", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--output", type=Path, default=Path("page-result"))
    parser.add_argument("--validate-only", action="store_true")
    args = parser.parse_args()
    manifest, body, images = load_bundle(args.bundle)
    if args.validate_only:
        print(f"Validated {len(images)} original Full HD screenshots and description")
    else:
        publish(manifest, body, images, args.output)
