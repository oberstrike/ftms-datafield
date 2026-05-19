#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const outRoot = path.join(root, "build", "generated", "garmin-variants");

const variants = [
  { key: "ascent", id: "8eb0b6152ef04aa7a1687c67ce46bfdf", label: "FTMS ASC", metric: "METRIC_ASCENT" },
  { key: "speed", id: "bc58d0ad4f40470f8a5303b891839681", label: "FTMS SPD", metric: "METRIC_SPEED" },
  { key: "distance", id: "f87c0efb71f44bf79f965d95f4261f89", label: "FTMS DST", metric: "METRIC_DISTANCE" },
  { key: "power", id: "29ac77102480444d947df8e8b3703b65", label: "FTMS PWR", metric: "METRIC_POWER" },
  { key: "cadence", id: "a2f6ed06f31c4598be28a8a37dd054db", label: "FTMS CAD", metric: "METRIC_CADENCE" },
  { key: "incline", id: "cbcb54002f7949deacbf25ef925977fe", label: "FTMS INC", metric: "METRIC_INCLINE" },
  { key: "heart-rate", id: "dfb711bb7b1648a1890688b85c7aa39f", label: "FTMS HR", metric: "METRIC_HEART_RATE" },
  { key: "elapsed", id: "ed07928d924147c38ce6ddcb0fe41937", label: "FTMS TIME", metric: "METRIC_ELAPSED" },
  { key: "resistance", id: "9ef0c46cf2224a9982f3c6b2b4c987df", label: "FTMS RES", metric: "METRIC_RESISTANCE" },
];

const command = process.argv[2] ?? "prepare";

if (command === "list") {
  for (const variant of variants) {
    console.log(`${variant.key}|${variant.label}`);
  }
  process.exit(0);
}

if (command !== "prepare") {
  console.error(`Unknown command: ${command}`);
  process.exit(2);
}

fs.rmSync(outRoot, { recursive: true, force: true });
fs.mkdirSync(outRoot, { recursive: true });

for (const variant of variants) {
  const dir = path.join(outRoot, variant.key);
  copyDir(path.join(root, "source"), path.join(dir, "source"));
  copyDir(path.join(root, "resources"), path.join(dir, "resources"));
  copyDir(path.join(root, "resources-fr970"), path.join(dir, "resources-fr970"));

  fs.writeFileSync(path.join(dir, "source", "FtmsVariant.mc"), variantSource(variant));

  const manifest = fs
    .readFileSync(path.join(root, "manifest.xml"), "utf8")
    .replace(/id="[0-9a-f]{32}"/, `id="${variant.id}"`);
  fs.writeFileSync(path.join(dir, "manifest.xml"), manifest);

  const stringsPath = path.join(dir, "resources", "strings", "strings.xml");
  const strings = fs
    .readFileSync(stringsPath, "utf8")
    .replace(/<string id="AppName">.*<\/string>/, `<string id="AppName">${variant.label}</string>`);
  fs.writeFileSync(stringsPath, strings);

  fs.writeFileSync(
    path.join(dir, "monkey.jungle"),
    [
      `project.manifest = ${path.relative(dir, path.join(dir, "manifest.xml"))}`,
      `base.sourcePath = ${path.relative(dir, path.join(dir, "source"))}`,
      `base.resourcePath = ${path.relative(dir, path.join(dir, "resources"))};${path.relative(dir, path.join(dir, "resources-fr970"))}`,
      "",
    ].join("\n"),
  );
}

function copyDir(from, to) {
  fs.mkdirSync(to, { recursive: true });
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const source = path.join(from, entry.name);
    const target = path.join(to, entry.name);
    if (entry.isDirectory()) {
      copyDir(source, target);
    } else if (entry.isFile()) {
      fs.copyFileSync(source, target);
    }
  }
}

function variantSource(variant) {
  return `module FtmsVariant {
    const METRIC_ASCENT = 0;
    const METRIC_SPEED = 1;
    const METRIC_DISTANCE = 2;
    const METRIC_POWER = 3;
    const METRIC_CADENCE = 4;
    const METRIC_INCLINE = 5;
    const METRIC_HEART_RATE = 6;
    const METRIC_ELAPSED = 7;
    const METRIC_RESISTANCE = 8;

    const KEY = "${variant.key}";
    const LABEL = "${variant.label}";
    const PRIMARY_METRIC = ${variant.metric};
}
`;
}
