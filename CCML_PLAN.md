# CCML Long-Term Plan

## Goal

Add a second credits authoring format alongside spreadsheets: `CCML` (`.ccml`), a Cinecred-native markup language for declaring credits as structured hierarchical content.

The design target is not "HTML for credits". It is a Cinecred-specific document format that maps directly onto the existing runtime model:

- `Credits`
- `Page`
- `Stage`
- `Compound`
- `Spine`
- `Block`
- `BodyElement`

This keeps rendering, styling, timing, and media behavior inside Cinecred's existing model instead of introducing a second layout engine.

## Why CCML

The current spreadsheet input works, but it has structural limits:

- Hierarchy is implied by rows and columns rather than expressed directly.
- Complex constructs like hooks, page grouping, melts, and runtime groups are hard to read and review.
- Validation is mostly semantic and row-oriented rather than document-structural.
- Refactoring large credits is awkward because meaning is spread across multiple columns.

CCML should improve:

- Readability for complex credits
- Version control diffs
- Programmatic generation
- Structural validation
- Long-term extensibility

## Non-Goals

- Do not embed a browser engine.
- Do not implement general HTML/CSS rendering.
- Do not create a separate styling system that competes with `styling.toml`.
- Do not replace spreadsheets immediately.

Spreadsheet support should remain first-class. CCML is an additional input format.

## Recommended Architecture

### Decision

Implement CCML as a direct parser to the internal credits model, not as a fake spreadsheet compiler.

### Why

Compiling CCML into a synthetic spreadsheet is attractive for a prototype, but it bakes spreadsheet constraints into the new format:

- hierarchy gets flattened again
- diagnostics become harder to relate to source markup
- some constructs will feel unnatural in XML only because the spreadsheet format requires them

For long-term maintenance, CCML should parse directly into the same model the renderer already consumes.

### Practical compromise

Phase 1 may still use a temporary lowering layer in tests or prototyping, but production code should end at:

`CCML file -> CCML AST -> semantic resolver -> Credits model`

not:

`CCML file -> Spreadsheet -> Table -> CreditsReader`

## File Format

- Default extension: `.ccml`
- Root project credits filename: `Credits.ccml`
- Encoding: UTF-8
- Comment syntax: XML comments
- Optional schema file for tooling: `ccml.xsd` or Relax NG equivalent later

XML is the best first syntax because:

- it matches the requested FXML-like feel
- hierarchical structure is explicit
- mature parsers already exist on the JVM
- schema validation is straightforward

## Core Design Principles

### 1. Structure mirrors runtime model

CCML should represent pages, stages, compounds, spines, and blocks explicitly.

### 2. Styling remains referential

CCML should reference existing style names from `styling.toml` rather than duplicate style definitions inline.

### 3. Inline rich text remains lightweight

Within text nodes, Cinecred's existing inline tag system can continue to work initially. A future revision may add XML-native inline spans.

### 4. Validation is split into two layers

- Structural validation: XML shape, required attributes, allowed children
- Semantic validation: style existence, invalid hook targets, incompatible page behavior, runtime issues

### 5. Migration must be incremental

CCML should be introduced without destabilizing spreadsheet users.

## Proposed CCML Schema Sketch

This is an initial shape, not a frozen standard.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<credits version="1">
  <page gapAfterFrames="12">
    <stage style="Scroll" runtimeGroup="crawlA" meltWithNext="false">
      <compound mode="scroll" hOffsetPx="0" vGapAfterPx="24">
        <spine selfVAnchor="middle" hookVAnchor="middle" hOffsetPx="0" vOffsetPx="0">
          <block contentStyle="Names">
            <head>CAST</head>
            <body>
              <text>Jane Doe</text>
              <text>John Smith</text>
            </body>
          </block>
          <block contentStyle="Jobs">
            <body>
              <text>Director</text>
              <text>Producer</text>
            </body>
          </block>
        </spine>
      </compound>
    </stage>

    <stage style="Card" cardRuntimeFrames="96" transitionStyle="Fade" transitionAfterFrames="12">
      <compound mode="card" vAnchor="middle" hOffsetPx="0" vOffsetPx="0">
        <spine>
          <block contentStyle="Centered">
            <body>
              <text>{{Style Title}}Special Thanks</text>
            </body>
          </block>
        </spine>
      </compound>
    </stage>
  </page>
</credits>
```

## Schema Elements

### `<credits>`

Root element.

Attributes:

- `version` required, integer-like string
- `spreadsheetName` optional compatibility/debug label

Children:

- zero or more `<runtimeGroup>`
- one or more `<page>`

### `<runtimeGroup>`

Optional explicit runtime-group declaration for named scroll groups.

Attributes:

- `name` required
- `runtimeFrames` required

Children:

- none

This is optional because a stage may also declare its named runtime group directly. If both exist, semantic validation must enforce consistency.

### `<page>`

Maps to `Page`.

Attributes:

- `gapAfterFrames` optional

Children:

- one or more `<stage>`

### `<stage>`

Maps to `Stage`.

Attributes:

- `style` required, references `PageStyle`
- `cardRuntimeFrames` optional
- `runtimeGroup` optional
- `runtimeFrames` optional
- `meltWithNext` optional boolean
- `transitionStyle` optional, references `TransitionStyle`
- `transitionAfterFrames` optional
- `vGapAfterPx` optional

Children:

- zero or more `<compound>`

Notes:

- `style` determines card vs scroll behavior.
- `runtimeGroup` is valid only for scroll stages.
- `meltWithNext` should follow the same semantic rules as the spreadsheet reader.

### `<compound>`

Maps to `Compound.Card` or `Compound.Scroll`.

Attributes:

- `mode` required: `card | scroll`
- `vAnchor` optional for card compounds: `top | middle | bottom`
- `hOffsetPx` optional
- `vOffsetPx` optional
- `vGapAfterPx` optional for scroll compounds

Children:

- one or more `<spine>`

Notes:

- semantic validation must ensure `mode` is compatible with the parent stage style behavior

### `<spine>`

Maps to `Spine`.

Attributes:

- `id` optional, local identifier for hook targets
- `hookTo` optional, references another spine `id`
- `hookVAnchor` optional: `top | middle | bottom`
- `selfVAnchor` optional: `top | middle | bottom`
- `hOffsetPx` optional
- `vOffsetPx` optional

Children:

- one or more `<block>`

### `<block>`

Maps to `Block`.

Attributes:

- `contentStyle` required, references `ContentStyle`
- `vGapAfterPx` optional
- `harmonizeHeadPartition` optional
- `harmonizeBodyPartition` optional
- `harmonizeTailPartition` optional

Children:

- optional `<head>`
- required `<body>`
- optional `<tail>`

### `<head>`, `<tail>`

Map to block head/tail rich text.

Attributes:

- none initially

Content:

- text nodes
- later optional `<span letterStyle="...">...</span>`
- later optional `<br/>`

### `<body>`

Container for body elements.

Children:

- `<text>`
- `<picture>`
- `<video>`
- `<blank>`

### `<text>`

Maps to `BodyElement.Str`.

Attributes:

- `letterStyle` optional default style

Content:

- plain text
- initial compatibility option: existing `{{Style ...}}`, `{{Pic ...}}`, `{{Video ...}}` inline tags allowed in text content
- future option: structured inline spans

### `<picture>`

Maps to `BodyElement.Pic`.

Attributes:

- `style` required, references `PictureStyle`

### `<video>`

Maps to `BodyElement.Tap`.

Attributes:

- `style` required, references `TapeStyle`

### `<blank>`

Maps to `BodyElement.Nil`.

Attributes:

- `letterStyle` optional

## Recommended Semantic Rules

The semantic layer should enforce at least:

- referenced page/content/letter/picture/tape/transition styles must exist
- card-only and scroll-only attributes may not be mixed
- `hookTo` must resolve within the current compound or another explicitly allowed scope
- runtime groups must not conflict on declared runtime
- empty pages and empty stages should follow current Cinecred behavior
- unknown elements and attributes should be surfaced as parser diagnostics
- ordering rules should be explicit, not inferred from ambiguous child order

## Text Model Strategy

### Short term

Reuse Cinecred's existing inline text markup inside XML text content:

```xml
<text>{{Style Title}}Executive Producers</text>
```

This minimizes scope because the current parser already understands that syntax.

### Medium term

Add XML-native inline runs:

```xml
<text>
  <span letterStyle="Title">Executive</span>
  <span letterStyle="Body"> Producers</span>
</text>
```

This should compile into the same styled string model currently produced by the spreadsheet parser.

## Parser Stack

Recommended pipeline:

1. Read XML into a CCML document AST.
2. Validate structural constraints.
3. Resolve references to styles, pictures, and tapes.
4. Lower AST into Cinecred `Credits` model objects.
5. Emit `ParserMsg` diagnostics with file/line/column context.

Recommended code organization:

- `projectio/ccml/CcmlDocument.kt`
- `projectio/ccml/CcmlParser.kt`
- `projectio/ccml/CcmlSemanticValidator.kt`
- `projectio/ccml/CcmlLowerer.kt`
- `projectio/ccml/CcmlFormat.kt` or equivalent loader entrypoint

## Integration Plan

### Phase 0: Design and test fixtures

Deliverables:

- this design note
- sample CCML files covering simple, medium, and complex credits
- fixture mapping from CCML examples to expected `Credits` model shapes

Exit criteria:

- format scope agreed
- first schema version narrowed

### Phase 1: File detection and loading hook

Changes:

- add `.ccml` to recognized credits extensions
- route `Credits.ccml` through a CCML loader in `ProjectIntake`
- preserve existing watcher behavior

Files likely affected:

- `src/main/kotlin/com/loadingbyte/cinecred/projectio/ProjectIntake.kt`

Exit criteria:

- app recognizes `Credits.ccml`
- load failures surface via existing diagnostics path

### Phase 2: Minimal parser

Scope:

- support one page
- support stages, compounds, spines, blocks
- support text-only body elements
- support style references

Not yet included:

- pictures
- tapes
- hooks
- runtime groups
- melt behavior

Exit criteria:

- non-trivial credits can render from CCML
- unit tests compare parsed `Credits` trees against expected structures

### Phase 3: Full semantic parity with spreadsheets

Add:

- pictures and tapes
- hooks and anchor behavior
- runtime groups
- melt and transition behavior
- partition harmonization
- richer diagnostics

Exit criteria:

- all major spreadsheet semantics have a CCML representation
- regression tests cover parity scenarios

### Phase 4: Authoring experience

Possible improvements:

- offer a template `Credits.ccml`
- add "new project with CCML" option
- documentation and examples
- optional export spreadsheet -> CCML
- optional export CCML -> spreadsheet for interoperability

Exit criteria:

- new users can start a CCML project without hand-authoring the entire file from scratch

### Phase 5: Tooling and schema hardening

Possible improvements:

- XSD or Relax NG schema
- better line/column diagnostics
- format version negotiation
- migration support between CCML versions

Exit criteria:

- schema is stable enough for external tooling and generators

## Migration Strategy

### Recommended rollout

1. Add CCML as opt-in.
2. Keep spreadsheets as the default template initially.
3. Add an alternate CCML template after parser stability.
4. Consider import/export tools only after semantic parity.

### Avoid

- forcing one-way migration from CSV/XLSX/ODS to CCML
- changing the styling file format at the same time
- mixing large UI changes into the parser rollout

## Testing Strategy

### Unit tests

- parse valid minimal documents
- reject malformed XML
- reject missing required references
- verify stage/compound/spine/block lowering
- verify line and style handling in text nodes

### Parity tests

Maintain fixture pairs:

- spreadsheet input
- equivalent CCML input
- assert equivalent `Credits` model output

This is the most important long-term safety net.

### Integration tests

- project intake recognizes `Credits.ccml`
- file watcher reloads on save
- diagnostics appear in UI for invalid CCML

## Risks

### 1. Schema overreach

If CCML tries to become a general-purpose page layout language, scope will explode.

Mitigation:

- keep the schema aligned to the existing model
- avoid arbitrary nesting and CSS-like styling

### 2. Semantic duplication

Spreadsheet semantics are already encoded in `CreditsReader`.

Mitigation:

- factor reusable semantic helpers out of `CreditsReader` where practical
- do not copy-paste business rules blindly

### 3. Diagnostics regression

Spreadsheet diagnostics are row/column oriented; CCML diagnostics need file/line/column context.

Mitigation:

- carry source locations through the AST
- map validation errors to `ParserMsg` consistently

### 4. Partial parity confusion

Users may assume CCML supports every spreadsheet feature immediately.

Mitigation:

- document supported features by version
- fail clearly on unsupported constructs

## Suggested First Milestone

The best first production milestone is:

- recognize `Credits.ccml`
- parse text-only credits with explicit page/stage/compound/spine/block structure
- resolve page styles and content styles by name
- reuse existing inline text tags in `<text>`
- render successfully in the preview

That milestone is small enough to ship, but meaningful enough to validate the format.

## Open Questions

- Should hooks be limited to local compound scope, or allow broader references?
- Should runtime groups be declared centrally, locally on stages, or both?
- Should inline XML spans be part of v1, or deferred in favor of existing `{{...}}` tags?
- Should CCML support includes or modular composition in v1?
- Should comments and processing instructions be preserved by any future formatter/exporter?

## Recommendation Summary

Build CCML as a Cinecred-native XML DSL with direct lowering into the internal credits model. Keep styling in `styling.toml`, keep spreadsheets supported, and pursue semantic parity in phases. The schema should be explicit, hierarchical, and intentionally narrower than HTML.
