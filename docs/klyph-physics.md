# Module klyph-physics

A 2D physics engine for klyph games: particles, soft bodies, ropes, chains and cloth, rigid bodies
with joints and motors, and a tile grid of solids they all collide with, solved with XPBD in
substeps. A game adds it as a dependency next to `klyph` and runs it with `App.addPhysics`; the
[Physics](../guide/physics.html) page of the guide walks through it.

Everything lives in one `PhysicsWorld`, stepped by a fixed `Duration`. Its stores hold the
bodies and hand out value-class handles such as `RigidBody` and `Particle`, so a step works on
plain arrays and allocates nothing. The same calls in the same order give the same results, to
the bit.

