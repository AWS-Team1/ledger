#!/usr/bin/env python3
"""Resolve affected ledger services from git --name-status output."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import PurePosixPath

SERVICES = ("balancereader", "ledgerwriter", "transactionhistory")
SERVICE_ROOTS = {service: f"src/ledger/{service}/" for service in SERVICES}
DOCKER_INPUTS = {"Dockerfile", ".dockerignore", "pom.xml"}
COMMON_DOCKER_PREFIXES = (".github/workflows/", ".github/scripts/")
COMMON_DOCKER_FILES = {".github/dependabot.yml"}
COMMON_VALIDATION_PREFIXES = (".mvn/",)
COMMON_VALIDATION_FILES = {"mvnw", "mvnw.cmd", "pom.xml", ".github/CODEOWNERS"}
DOC_PREFIXES = ("docs/", ".github/ISSUE_TEMPLATE/", ".github/PULL_REQUEST_TEMPLATE/")
DOC_FILES = {"README.md", "LICENSE", "NOTICE", ".gitignore", ".gitattributes"}


def normalize(path: str) -> str:
    normalized = PurePosixPath(path.strip().replace("\\", "/")).as_posix()
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def paths_from_name_status(text: str) -> list[str]:
    paths: list[str] = []
    for line in text.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        status = parts[0]
        if status.startswith(("R", "C")):
            if len(parts) != 3:
                raise ValueError(f"invalid rename/copy entry: {line!r}")
            paths.extend((normalize(parts[1]), normalize(parts[2])))
        else:
            if len(parts) != 2:
                raise ValueError(f"invalid name-status entry: {line!r}")
            paths.append(normalize(parts[1]))
    return sorted(set(paths))


def is_docs_only(path: str) -> bool:
    return path in DOC_FILES or path.endswith(".md") or any(
        path.startswith(prefix) for prefix in DOC_PREFIXES
    )


def classify(paths: list[str]) -> tuple[list[str], list[str]]:
    services: set[str] = set()
    docker_services: set[str] = set()
    unknown: list[str] = []
    for raw_path in paths:
        path = normalize(raw_path)
        if is_docs_only(path):
            continue
        matched = False
        for service, root in SERVICE_ROOTS.items():
            if path.startswith(root):
                matched = True
                services.add(service)
                if path.removeprefix(root) in DOCKER_INPUTS:
                    docker_services.add(service)
                break
        if matched:
            continue
        if path in COMMON_DOCKER_FILES or any(path.startswith(p) for p in COMMON_DOCKER_PREFIXES):
            services.update(SERVICES)
            docker_services.update(SERVICES)
        elif path in COMMON_VALIDATION_FILES or any(path.startswith(p) for p in COMMON_VALIDATION_PREFIXES):
            services.update(SERVICES)
        else:
            unknown.append(path)
    if unknown:
        raise ValueError("unclassified paths; update CI mapping: " + ", ".join(sorted(unknown)))
    ordered_services = sorted(services)
    ordered_docker = sorted(docker_services)
    if not set(ordered_docker).issubset(ordered_services):
        raise AssertionError("docker_services must be a subset of services")
    return ordered_services, ordered_docker


def emit(services: list[str], docker_services: list[str]) -> None:
    compact = {"separators": (",", ":")}
    print(f"services={json.dumps(services, **compact)}")
    print(f"docker_services={json.dumps(docker_services, **compact)}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", help="git diff --name-status file")
    parser.add_argument("--all", action="store_true", help="select every service")
    args = parser.parse_args()
    try:
        if args.all:
            emit(list(SERVICES), list(SERVICES))
        else:
            if not args.input:
                parser.error("--input is required unless --all is used")
            with open(args.input, encoding="utf-8") as file:
                paths = paths_from_name_status(file.read())
            emit(*classify(paths))
    except (OSError, ValueError, AssertionError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
