# Material Color Utilities subset

- Upstream: https://github.com/material-foundation/material-color-utilities
- Revision: `5b3618b16fdc3825e21d5679bafd144662088ea1`
- License: Apache-2.0; see `LICENSE` and copyright headers in each Java file.
- Source: upstream `java/{quantize,hct,utils,score}`.
- Included: Celebi quantizer, Score, HCT and their transitive Java dependencies (15 files).
- Local changes: package/import relocation to
  `com.bobo.auralis.mobile.thirdparty.materialcolorutilities`; `QuantizerWu` switch
  statements rewritten to equivalent Java 11 syntax; no algorithm changes.

Keep this subset pinned. When updating, copy the required upstream files, relocate
their packages/imports, retain license headers and rerun `ArtworkColorAlgorithmTest`.
Application-specific neutral fallback and theme tone policy live in
`ui/demo/ArtworkColorAlgorithm.kt`, outside the upstream implementation.
