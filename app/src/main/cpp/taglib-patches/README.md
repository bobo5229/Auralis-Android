# TagLib patches

`app/src/main/cpp/taglib` is a git submodule pinned to an upstream TagLib commit. The patches in
this directory are applied to that checkout by `app/src/main/cpp/CMakeLists.txt` during the CMake
configure step, so no fork is needed and a fresh clone builds the same code.

Application is idempotent: a patch that is already applied is skipped, and a patch that no longer
fits (because the pinned revision changed) fails the configure step with a clear message instead of
building silently wrong code.

After a successful application the configure step reads the patched file back and checks for
`TAGLIB_MP4TAG_PATCH_MARKER` (set in `CMakeLists.txt`, currently the `merged.append(...)` line
introduced by patch 0001). If the marker is missing the build stops with a fatal error, so an
unpatched TagLib is never built. **When a patch is regenerated, keep that marker in sync with the
patched code, otherwise the configure step will fail.**

Exit codes are always compared with `STREQUAL`: `execute_process` returns an error *string* when it
cannot run the program, and `if(<string> EQUAL 0)` treats a non-numeric string as `0`, which would
silently skip the patch.

After a build the submodule working tree shows the modified files. That is expected;
`git -C app/src/main/cpp/taglib checkout -- .` restores it and the next configure re-applies.

## 0001-mp4-merge-duplicate-stringlist-items.patch

`MP4::Tag::addItem()` kept only the first atom of a given name and logged
`Ignoring duplicate atom`, so every later atom was dropped before it could reach the JNI layer.
Real files do store several values of one field in several atoms sharing one name: the reference
sample `Charli xcx - 360.m4a` has two `----:com.apple.iTunes:GENRE` atoms (Hyperpop, Electropop) and
six `©wrt` (composer) atoms.

The patch merges the two string lists, in file order, when both the existing and the incoming item
are `Item::Type::StringList`. All other duplicate item types keep upstream behaviour, and the merge
does not trim, split, de-duplicate or case-fold anything.

There is no way to fix this outside TagLib: `MP4::File` exposes no public atom tree, and
`MP4::Tag::itemMap()` is already built by the time the JNI layer sees it.

Regression test: `app/src/androidTest/java/com/bobo/auralis/mobile/library/metadata/Mp4DuplicateAtomTest.kt`
against the fixture `app/src/androidTest/assets/mp4/duplicate-mp4-atoms.m4a`.

To regenerate the patch after editing, apply the change to the submodule and run:

    git -C app/src/main/cpp/taglib diff -- taglib/mp4/mp4tag.cpp > \
        app/src/main/cpp/taglib-patches/0001-mp4-merge-duplicate-stringlist-items.patch
