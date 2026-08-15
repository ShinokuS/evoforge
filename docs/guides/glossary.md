# Glossary

**Authoritative owner** — the single system that may mutate a runtime fact.

**Capability** — a narrow interface giving a consumer only the reads or semantic writes it needs.

**Command** — external/control intent routed to the domain owner; not a universal internal message.

**Definition** — immutable runtime description compiled from content source data.

**Shape** — declarative local geometry contributing neutral physical facts and structural traversal roles.

**Standing Z** — the navigation plane on which an actor stands. Under the current Shape model, its supporting terrain anchor is normally one Z below.

**Navigation** — actor-independent structural edge existence.

**TransitionCost** — actor-independent intrinsic cost of an already-valid directed structural edge.

**MoverTraversalConstraint** — current mover/environment rule that may reject a structurally valid edge without changing Navigation topology; Water wading is the first production example.

**Occupancy** — present-tense dynamic cell availability and execution reservation state, intentionally separate from Navigation and Spatial.

**MovementAction** — domain-owned in-flight adjacent movement state. It does not replace the authoritative Spatial position.

**MovementClaim** — long-lived locomotion ownership token used by route-level controllers such as MoveTo across planning and child edges.

**MoveTo** — route-level locomotion intent that consumes disposable Pathfinder advice and executes every real edge through Movement.

**SurfaceWaterStorage** — finite free Water retained on a supporting terrain surface before same-Z horizontal runoff becomes mobile. It is not SoilMoisture.

**SoilMoisture** — finite Water retained by absorbent terrain and owned independently from free `WaterState`.

**WorldBounds** — optional inclusive finite runtime coordinate box. Outside it, shared `WorldGeometryLookup` presents closed `FullShape` geometry; it is not yet a chunk/streaming model.

**SimulationView** — grouped read-only runtime capabilities exposed to observers such as presentation.

**Presentation binding** — specialized adapter that knows one concrete domain variant and exposes its presentation representation through a generic registry.

**Surface view** — presentation perspective that projects the highest authoritative terrain/Water surface per visible XY column. It does not flatten or mutate the simulation world.

**Development Journal** — non-normative dated design/history notes. It preserves context but never overrides Architecture, Decisions or System pages.

**main** — stable accepted milestone branch.

**develop** — integration branch for the next milestone.
