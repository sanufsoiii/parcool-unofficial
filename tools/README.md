# tools/

`verify_mixins.py` checks every `@Mixin` target and every `@Inject` / `@Redirect` / `@Modify*` /
`@Wrap*` / `@Accessor` / `@Invoker` / `@Shadow` target under `common/src/main/java` against a
resolved mojmap Minecraft jar, so a target that silently stops matching is caught without booting
the game.

```bash
JAR=$(ls ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.10-loom.mappings.*/*.jar | head -1)
python3 tools/verify_mixins.py "$JAR" common/src/main/java
```

It needs `python3` and a JDK on `PATH` (it shells out to `unzip` and `javap`).

## Why it self-tests

The checker is only useful if it *can* fail. An earlier port verified its mixin targets with a regex
like `\(([^)]*)\)`, which stops at the first `)` — i.e. inside the first method descriptor — so it
silently skipped every target that had a descriptor and reported "0 problems" about a completely
broken tree. Two things guard against a repeat here:

* descriptors are parsed with balanced parentheses, and the method name is taken as the identifier
  immediately before the argument list (not as the last whitespace-separated token, which breaks on
  a generic signature such as `extractRenderState(AvatarlikeEntity, Foo, float)`);
* every run first executes `self_test()`, which feeds the *same* code path three targets — one with
  the wrong arity, one with a wrong method name, and one genuinely correct — and refuses to report
  anything unless the first two are flagged and the third stays silent. A checker that flags all
  three is as useless as one that flags none.

The self-test is what makes "no problems found" a statement worth anything.
