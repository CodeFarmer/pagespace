package io.gluth.pagespace.backend

import io.gluth.pagespace.domain.Page

class MockContentBackend : ContentBackend {

    companion object {
        private val PAGES:  Map<String, Page>
        private val LINKS:  Map<String, List<Page>>
        private val BODIES: Map<String, String>

        init {
            val physics     = p("mock:physics",       "Physics")
            val math        = p("mock:math",           "Mathematics")
            val quantum     = p("mock:quantum",        "Quantum Mechanics")
            val relativity  = p("mock:relativity",     "Relativity")
            val logic       = p("mock:logic",          "Logic")
            val philosophy  = p("mock:philosophy",     "Philosophy")
            val calculus    = p("mock:calculus",       "Calculus")
            val thermo      = p("mock:thermodynamics", "Thermodynamics")
            val optics      = p("mock:optics",         "Optics")
            val chemistry   = p("mock:chemistry",      "Chemistry")
            val biology     = p("mock:biology",        "Biology")
            val statistics  = p("mock:statistics",     "Statistics")
            val probability = p("mock:probability",    "Probability")
            val geometry    = p("mock:geometry",       "Geometry")
            val algebra     = p("mock:algebra",        "Algebra")
            val cs          = p("mock:cs",             "Computer Science")

            val pages = LinkedHashMap<String, Page>()
            for (pg in listOf(physics, math, quantum, relativity, logic, philosophy,
                              calculus, thermo, optics, chemistry, biology,
                              statistics, probability, geometry, algebra, cs)) {
                pages[pg.id] = pg
            }
            PAGES = pages

            val links = LinkedHashMap<String, List<Page>>()
            links[physics.id]     = listOf(math, quantum, relativity, thermo, optics)
            links[math.id]        = listOf(physics, logic, calculus, statistics, algebra, geometry)
            links[quantum.id]     = listOf(physics, relativity, optics)
            links[relativity.id]  = listOf(physics, math)
            links[logic.id]       = listOf(math, philosophy, cs, algebra)
            links[philosophy.id]  = listOf(logic)
            links[calculus.id]    = listOf(math, physics, statistics)
            links[thermo.id]      = listOf(physics, chemistry, math)
            links[optics.id]      = listOf(physics, quantum)
            links[chemistry.id]   = listOf(physics, math, biology)
            links[biology.id]     = listOf(chemistry, statistics)
            links[statistics.id]  = listOf(math, probability, biology, calculus)
            links[probability.id] = listOf(math, statistics)
            links[geometry.id]    = listOf(math, physics)
            links[algebra.id]     = listOf(math, logic, cs)
            links[cs.id]          = listOf(math, logic, algebra)
            LINKS = links

            val bodies = LinkedHashMap<String, String>()
            bodies[physics.id] =
                "<h1>Physics</h1><p>Physics is the natural science that studies matter, energy, and their interactions. " +
                "Key branches include <a href=\"mock:quantum\">Quantum Mechanics</a>, " +
                "<a href=\"mock:relativity\">Relativity</a>, " +
                "<a href=\"mock:thermodynamics\">Thermodynamics</a>, and " +
                "<a href=\"mock:optics\">Optics</a>. " +
                "Underpinned by <a href=\"mock:math\">Mathematics</a>.</p>"
            bodies[math.id] =
                "<h1>Mathematics</h1><p>The study of numbers, structures, and patterns. " +
                "Core areas: <a href=\"mock:logic\">Logic</a>, " +
                "<a href=\"mock:calculus\">Calculus</a>, " +
                "<a href=\"mock:algebra\">Algebra</a>, " +
                "<a href=\"mock:geometry\">Geometry</a>, and " +
                "<a href=\"mock:statistics\">Statistics</a>. " +
                "Applied throughout <a href=\"mock:physics\">Physics</a>.</p>"
            bodies[quantum.id] =
                "<h1>Quantum Mechanics</h1><p>Describes nature at atomic and subatomic scales. " +
                "Related to <a href=\"mock:physics\">Physics</a>, " +
                "<a href=\"mock:relativity\">Relativity</a>, and " +
                "<a href=\"mock:optics\">Optics</a>.</p>"
            bodies[relativity.id] =
                "<h1>Relativity</h1><p>Einstein's special and general theories describe space, time, and gravity. " +
                "See also: <a href=\"mock:physics\">Physics</a>, " +
                "<a href=\"mock:math\">Mathematics</a>.</p>"
            bodies[logic.id] =
                "<h1>Logic</h1><p>The study of valid inference and formal reasoning. " +
                "Related: <a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:philosophy\">Philosophy</a>, " +
                "<a href=\"mock:algebra\">Algebra</a>, " +
                "<a href=\"mock:cs\">Computer Science</a>.</p>"
            bodies[philosophy.id] =
                "<h1>Philosophy</h1><p>Examines fundamental questions about existence, knowledge, and ethics. " +
                "Grounded in <a href=\"mock:logic\">Logic</a>.</p>"
            bodies[calculus.id] =
                "<h1>Calculus</h1><p>The mathematical study of continuous change — differentiation and integration. " +
                "Related: <a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:physics\">Physics</a>, " +
                "<a href=\"mock:statistics\">Statistics</a>.</p>"
            bodies[thermo.id] =
                "<h1>Thermodynamics</h1><p>Studies heat, work, temperature, and energy transfer. " +
                "Related: <a href=\"mock:physics\">Physics</a>, " +
                "<a href=\"mock:chemistry\">Chemistry</a>, " +
                "<a href=\"mock:math\">Mathematics</a>.</p>"
            bodies[optics.id] =
                "<h1>Optics</h1><p>The branch of physics studying light and its interactions with matter. " +
                "Related: <a href=\"mock:physics\">Physics</a>, " +
                "<a href=\"mock:quantum\">Quantum Mechanics</a>.</p>"
            bodies[chemistry.id] =
                "<h1>Chemistry</h1><p>Studies composition, structure, and reactions of matter. " +
                "Related: <a href=\"mock:physics\">Physics</a>, " +
                "<a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:biology\">Biology</a>.</p>"
            bodies[biology.id] =
                "<h1>Biology</h1><p>The science of living organisms and life processes. " +
                "Uses <a href=\"mock:chemistry\">Chemistry</a> and " +
                "<a href=\"mock:statistics\">Statistics</a>.</p>"
            bodies[statistics.id] =
                "<h1>Statistics</h1><p>The science of collecting, analysing, and interpreting data. " +
                "Rooted in <a href=\"mock:math\">Mathematics</a> and " +
                "<a href=\"mock:probability\">Probability</a>. " +
                "Applied in <a href=\"mock:biology\">Biology</a> and " +
                "<a href=\"mock:calculus\">Calculus</a>.</p>"
            bodies[probability.id] =
                "<h1>Probability</h1><p>Quantifies uncertainty and likelihood of events. " +
                "Related: <a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:statistics\">Statistics</a>.</p>"
            bodies[geometry.id] =
                "<h1>Geometry</h1><p>Studies shapes, sizes, and properties of figures and spaces. " +
                "Related: <a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:physics\">Physics</a>.</p>"
            bodies[algebra.id] =
                "<h1>Algebra</h1><p>Studies symbols and the rules for manipulating them. " +
                "Related: <a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:logic\">Logic</a>, " +
                "<a href=\"mock:cs\">Computer Science</a>.</p>"
            bodies[cs.id] =
                "<h1>Computer Science</h1><p>The study of computation, algorithms, and information processing. " +
                "Grounded in <a href=\"mock:math\">Mathematics</a>, " +
                "<a href=\"mock:logic\">Logic</a>, and " +
                "<a href=\"mock:algebra\">Algebra</a>.</p>"
            BODIES = bodies
        }

        private fun p(id: String, title: String): Page = Page(id, title)
    }

    override fun defaultPage(): Page = PAGES["mock:physics"]!!

    override fun fetchBody(id: String): String = BODIES[id] ?: throw PageNotFoundException(id)

    override fun fetchLinks(id: String): List<Page> = LINKS[id] ?: throw PageNotFoundException(id)

    override fun searchPages(query: String): List<Page> =
        PAGES.values.filter { it.title.contains(query, ignoreCase = true) }
}
