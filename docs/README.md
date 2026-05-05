# Docs Guide

## Purpose
This directory stores product-facing and development-facing documents that explain feature requirements, design intent, and historical requirement changes.

The goal of this guide is to keep document naming and usage consistent so new docs remain easy to find and maintain.

## Current Document Types

### `PRD`
Use `PRD` for feature requirement documents that developers can directly implement from.

Typical contents:
- background
- goals
- non-goals
- scenarios
- interaction rules
- implementation scope
- acceptance criteria

### `CHANGELOG`
Use `CHANGELOG` for historical records of requirement or behavior changes over time.

Typical contents:
- date-based entries
- implemented/fixed scope
- requirement summary

This is a history log, not a full standalone implementation spec.

### `RFC`
Use `RFC` for proposals that are still under discussion and not yet finalized.

Typical contents:
- competing options
- tradeoffs
- open questions
- recommended direction

### `TECH_SPEC`
Use `TECH_SPEC` for implementation design documents after a product direction is already settled.

Typical contents:
- architecture
- state machine
- APIs
- platform differences
- rollout plan

## Naming Convention

### Recommended Format
Use:

`<DOC_TYPE>_<FEATURE_NAME>.<language>.md`

Examples:
- `PRD_MAP_EMPTY_SCREEN_RESCUE.zh-CN.md`
- `PRD_MAP_EMPTY_SCREEN_RESCUE.en.md`
- `RFC_MAP_CAMERA_RESCUE_OPTIONS.en.md`
- `TECH_SPEC_MAP_CAMERA_EMPTY_SCREEN_RESCUE.zh-CN.md`

### Language Suffix
Use explicit language suffixes when the same content exists in multiple languages:
- `zh-CN`
- `en`

If a document exists in only one language, still prefer adding the language suffix for consistency.

### Feature Name Style
Use uppercase snake case for feature names:
- `MAP_EMPTY_SCREEN_RESCUE`
- `CAMERA_VIEW_READABILITY`
- `SCAN_SCREEN_CODE_MODE`

Avoid:
- spaces
- mixed separators
- vague names such as `optimization`, `notes`, `draft`, `misc`

## File Naming Rules

### Good Examples
- `PRD_CAMERA_VIEW_READABILITY.zh-CN.md`
- `PRD_MAP_EMPTY_SCREEN_RESCUE.en.md`
- `CHANGELOG_REQUIREMENTS.md`

### Avoid
- `MAP_EMPTY_SCREEN_RESCUE_PRD.md`
  - doc type should come first
- `CAMERA_UI_OPTIMIZATION.md`
  - missing doc type and language
- `new_feature_notes.md`
  - unclear purpose

## Current Recommended Mapping

### Product Requirement Docs
- [PRD_CAMERA_VIEW_READABILITY.zh-CN.md](./PRD_CAMERA_VIEW_READABILITY.zh-CN.md)
- [PRD_MAP_EMPTY_SCREEN_RESCUE.zh-CN.md](./PRD_MAP_EMPTY_SCREEN_RESCUE.zh-CN.md)
- [PRD_MAP_EMPTY_SCREEN_RESCUE.en.md](./PRD_MAP_EMPTY_SCREEN_RESCUE.en.md)

### Historical Requirement Log
- [requirements-changelog.md](./requirements-changelog.md)

### Demo Media
Photo flow demo:
🎥 Demo: https://youtu.be/pon8pqhOicM

Driving map follow demo:
🎥 Demo: https://youtu.be/DM9m_VVtZhA

Map overview reference:

<img src="./media/Map%20overlook.png" alt="Map overview reference" width="360" />

Large parcel marker reference:

<img src="./media/Map%20with%20large%20package%20sign.png" alt="Large parcel marker reference" width="360" />

Multi-parcel map reference:

<img src="./media/Map%20with%20multipe%20packages.png" alt="Multi parcel map reference" width="360" />

## Suggested Future Cleanup

### Optional Rename
The historical log can stay as-is, but if full normalization is desired later, rename:
- `requirements-changelog.md`
to
- `CHANGELOG_REQUIREMENTS.md`

This rename is optional because changelog files are often referenced manually and do not need strict pairing by language.

## Authoring Rules

1. If the document defines a feature for development, default to `PRD`.
2. If the document records what changed across dates or versions, use `CHANGELOG`.
3. If the direction is still being debated, use `RFC`.
4. If the product direction is settled and the remaining work is technical design, use `TECH_SPEC`.
5. Keep one topic per file. Do not mix unrelated features into one PRD.
