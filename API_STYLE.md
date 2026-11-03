# klyph API style

This spec governs the shape of every public declaration in klyph. It is adapted
from how the Kotlin standard library is designed, so that klyph reads like a
natural extension of the stdlib and users can guess an API before they look it up.

"MUST" rules are checked in code review and block a merge. "SHOULD" rules need a
written reason in the PR when broken.

Scope: public and `@PublishedApi internal` declarations. Private and internal
code is free to differ, but should not contradict the naming vocabulary.

## 1. Members and extensions

- A class or interface MUST contain only its core concept: state, overrides,
  operators, and the minimal operations that need private access.
- Everything else MUST be an extension function or extension property, declared
  in a file named after the receiver or topic (`Graphs.kt`, `Nodes.kt`), not in a
  file created only to hold extensions of one class.
- An extension MUST have the narrowest visibility that works. Do not make a
  helper public because it was convenient inside the library.
- Do not declare default-importable extensions on `Any`, `Any?` or an
  unconstrained type parameter. They pollute completion for every user.

```kotlin
// Core concept: members.
public interface Graph<N> {
    public val nodes: Set<N>
    public fun neighborsOf(node: N): Set<N>
}

// Everything derived: extensions.
public fun <N> Graph<N>.isEmpty(): Boolean = nodes.isEmpty()
public fun <N> Graph<N>.degreeOf(node: N): Int = neighborsOf(node).size
```

## 2. Naming vocabulary

Names MUST follow this vocabulary. If a new name does not fit, extend this table
in the same PR rather than inventing a one-off.

| Pattern | Meaning | Example |
|---|---|---|
| `toX()` | Returns a new, independent copy of type X. | `toList()`, `toGraph()` |
| `asX()` | Returns a view or wrapper backed by the receiver. | `asSequence()`, `asReadOnly()` |
| `xOf(...)` | Top-level factory from explicit elements. | `graphOf(a to b)` |
| `buildX { }` | Top-level factory through a builder DSL. | `buildGraph { edge(a, b) }` |
| `emptyX()` | Shared immutable empty instance. | `emptyGraph()` |
| `X.of(...)` | Companion factory for a value class whose constructor takes the raw representation, building it from the value users think in. | `CollisionLayers.of(3)` |
| `onX { }` | Builder function that registers an action run when `X` happens. | `buildScene("title") { onEnter { } }` |
| `xOrNull()` | Same as `x()` but returns `null` instead of throwing. May stand alone when absence is normal rather than an error. | `firstOrNull()`, `nodeOrNull(id)`, `parentOrNull(child)` |
| `xOf(argument)` | Member or extension that returns the `x` associated with [argument]. Not a factory. | `neighborsOf(node)`, `childrenOf(parent)`, `durationOf(phase)` |
| `xOrElse { }` | Same as `x()` but calls the lambda instead of throwing. | `getOrElse { default }` |
| `xOrDefault(value)` | Same as `x()` but returns the value instead of throwing. | `getOrDefault(key, 0)` |
| `xTo(destination, ...)` | Writes results into a caller-supplied mutable collection and returns it. | `mapTo(list) { }` |
| `xIndexed { i, e -> }` | Same as `x` but the lambda also receives the index. | `mapIndexed` |
| `xNotNull` | Same as `x` but drops `null` results. | `mapNotNull`, `filterNotNull` |
| `sort()` / `sorted()` | Verb mutates the receiver in place. Past participle returns a copy. | `reverse()` vs `reversed()` |
| `MutableX` | Mutable counterpart of a read-only interface `X`. | `MutableGraph` |
| `AbstractX` | Skeletal implementation for users who extend `X`. | `AbstractGraph` |
| `isX` / `hasX` | Boolean property or function. No `getIsX`, no bare adjectives. | `isEmpty`, `hasCycle` |
| `setX(handle, value)` | Changes property `x` of the thing a value-class handle refers to, on the service that owns it. Only for handles, which cannot hold mutable properties; everything else uses `var x`. | `audio.setVolume(voice, 0.5f)`, `audio.setPan(voice, -1f)` |
| `XImpl` | Private or internal implementation of `X`. Never public. | `GraphImpl` |

General rules:

- Classes are nouns. Functions are verbs or verb phrases. Properties are nouns
  or `is`/`has` adjectives.
- Take names from the problem domain. Invent a word only when the domain has
  none, and define it in KDoc on first use.
- Do not use `Manager`, `Helper`, `Util`, `Wrapper`, `Data`, `Info` in names.
- A name MUST be discoverable through completion: a user typing the receiver
  and a dot should find it by its first word. Prefer `nodeOrNull` to
  `findNodeIfPresent`.
- Factory functions that construct exactly one type SHOULD be named after that
  type (`fun Graph(...): Graph`). Otherwise use a verb name on a companion or
  top level (`Graph.fromAdjacency(...)`).

## 3. Parameters and overloads

- Prefer default parameter values to a set of overloads. Overloads that remain
  MUST sit next to each other in the file.
- A parameter MUST NOT be a bare `Boolean` that selects behavior. Split the
  function (`map` / `mapNotNull`) or take an enum.
- Parameters sharing a primitive type, or any `Boolean` parameter, are called
  with named arguments inside the library and documented so users do the same.
- A function taking one lambda MUST take it as the last parameter so the
  trailing-lambda form works.
- Prefer composing small operations over adding configuration parameters to
  one big function. Add a new operator before you add a fifth parameter.
- Builders take a lambda with receiver as the last parameter. Values the builder
  cannot work without MUST be normal parameters, not builder properties, because
  a builder cannot enforce required fields at compile time.

```kotlin
public fun <N> buildGraph(directed: Boolean = false, builder: GraphBuilder<N>.() -> Unit): Graph<N>
```

- Expression-shaped lambdas (`transform`, `predicate`, `selector`) use the same
  parameter names the stdlib uses so users can read them without the docs.

## 4. Types in signatures

- Public signatures MUST accept and return read-only `List`, `Set`, `Map`,
  `Collection`, `Iterable` or `Sequence`. Never `MutableX`, `ArrayList`, or
  `HashMap` in a public signature.
- Arrays MUST NOT appear in public signatures. If an array is unavoidable, copy
  it on the way in and on the way out. One exception: audio types in
  `klyph.audio` and `klyph.platform` may take a `FloatArray` sample buffer
  without copying, such as `AudioRenderer.render` and `PcmStream.read`, because
  the mix path runs on the audio thread and must not allocate.
- Return `List` for eager operations and `Sequence` for lazy ones. A function on
  `Sequence` MUST stay lazy and MUST NOT consume the receiver before the result
  is iterated.
- Identifiers, units and other non-arithmetic scalars MUST be wrapped in a
  `value class`, not passed as `Int`, `Long` or `String`. klyph has no JVM
  target, so value classes carry no `@JvmInline`; the shared native source set
  rejects that annotation when several targets are built.
- Closed sets of variants are `sealed interface` or `sealed class` so users get
  exhaustive `when` and cannot add cases the library does not handle.
- `data class` MUST NOT be public. It freezes constructor and `copy` shape and
  its `toString` becomes part of the contract. Write a normal class with
  explicit `equals`, `hashCode` and `toString`.
- Every public type that holds state MUST override `toString` in a consistent,
  readable format that contains no secrets. Document the format only if users
  are allowed to parse it.
- No global mutable state and no stateful top-level functions. Anything that
  behaves like a global (clock, random source, registry) is a parameter with a
  sensible default instance, in the style of `Clock.System`.
- Public functions MUST declare Kotlin types explicitly. Platform types never
  leak into a signature.

## 5. Failure contract

- Validate arguments with `require(...)`. It throws `IllegalArgumentException`.
- Validate receiver or library state with `check(...)`. It throws
  `IllegalStateException`.
- Use the stdlib exception for the stdlib meaning:
  - `NoSuchElementException` when a requested element does not exist.
  - `IndexOutOfBoundsException` for a bad index.
  - `UnsupportedOperationException` when a read-only view is mutated.
  - `IllegalArgumentException` when a single element was expected and several were found.
- Exceptions that carry library meaning extend a single public root
  `KlyphException`. Low-level exceptions caught inside the library are wrapped
  with the original as `cause`, never swallowed.
- Messages are one sentence, sentence case, end with a period, and interpolate
  the offending value.

```kotlin
require(count >= 0) { "Requested element count $count is less than zero." }
throw NoSuchElementException("Graph is empty.")
throw IndexOutOfBoundsException("Index $index is out of bounds for size $size.")
```

- Lazy messages take `lazyMessage: () -> Any`, matching `require` and `check`.
- A throwing operation SHOULD have an `OrNull` sibling. Do not add an `OrNull`
  sibling to a function that cannot fail.
- Do not return `Result<T>` from public API. Throw, or offer an `OrNull`
  variant.
- Every exception a public function can throw on valid Kotlin input MUST appear
  in its KDoc as `@throws`, and the failure condition MUST also be stated in the
  description.

## 6. Operators, infix and properties

- `infix` is allowed only when both operands play a similar role (`to`, `and`,
  `zip`) and the function does not mutate the receiver.
- `operator` overloads MUST keep the mathematical or collection meaning of the
  symbol. `plus` returns a new value, `plusAssign` mutates, never both on the
  same type.
- Use a property instead of a function only when all hold: it cannot throw, it
  is O(1) or cached, and it returns the same value while state is unchanged.
  Otherwise it is a function.

## 7. KDoc

Every public declaration MUST have KDoc, except an override that adds nothing.

- The first sentence is third-person indicative and starts with a verb:
  "Returns ...", "Creates ...", "Adds ...", "Throws ...". It MUST say what the
  function does for the caller, not restate the signature.
- Refer to parameters inline with `[name]`. Use `@param` and `@return` only when
  the explanation is too long for the prose.
- State valid input ranges and what happens on invalid input in the description.
- Order of tags: description, blank line, `@throws` lines, blank line, `@sample`.
- Overloads on different receivers reuse the same sentence with only the
  receiver noun changed ("collection", "graph", "sequence").
- Link related declarations with `[Name]` or `@see`.
- Plain English. No jargon that the description does not define, no Latin.

```kotlin
/**
 * Returns the first node reachable from [start] that satisfies [predicate].
 *
 * The search is breadth-first and visits each node at most once.
 *
 * @throws NoSuchElementException if no reachable node matches.
 *
 * @sample samples.graph.Traversal.firstReachable
 */
public fun <N> Graph<N>.firstReachable(start: N, predicate: (N) -> Boolean): N
```

## 8. Samples

Every public function with non-trivial behavior MUST have a `@sample` that
points at a function in `samples/`, compiled and run as a test.

- A sample shows the common case, not corner cases.
- Each sample is self-contained: no shared fixtures, no helper calls.
- Allowed assertions: `assertPrints`, `assertTrue`, `assertFalse`,
  `assertFails`, `assertFailsWith`. Nothing else, so the docs build can turn
  samples into readable snippets.
- Sample functions are named after the function they show, without `test`.
- One sample may cover several closely related overloads.

```kotlin
class Traversal {
    @Sample
    fun firstReachable() {
        val graph = graphOf(1 to 2, 2 to 3)
        assertPrints(graph.firstReachable(1) { it > 1 }, "2")
    }
}
```

## 9. Review checklist

Copy into the PR description and tick each item.

- [ ] Members hold only the core concept; derived operations are extensions.
- [ ] Every new name uses the vocabulary table, or the table was extended.
- [ ] No `Boolean` behavior flags, lambda is last, defaults over overloads.
- [ ] Signatures use read-only collections, no arrays, no `data class`, no platform types.
- [ ] Value classes for identifiers and units; sealed types for closed sets.
- [ ] `require`/`check` for validation; exception types and messages follow section 5.
- [ ] Throwing functions have an `OrNull` sibling where failure is expected.
- [ ] `infix`/`operator`/property use passes section 6.
- [ ] KDoc on every public declaration, verb-first, `@throws` for each exception.
- [ ] `@sample` present and runs as a test.
- [ ] `toString` on every stateful public type.
