package io.gluth.pagespace.backend;

import io.gluth.pagespace.domain.Page;

import java.util.*;

public class MockContentBackend implements ContentBackend {

    private static final Map<String, Page>       PAGES;
    private static final Map<String, List<Page>> LINKS;
    private static final Map<String, String>     BODIES;

    static {
        // --- pages ---
        Page physics     = p("mock:physics",      "Physics");
        Page math        = p("mock:math",          "Mathematics");
        Page quantum     = p("mock:quantum",       "Quantum Mechanics");
        Page relativity  = p("mock:relativity",    "Relativity");
        Page logic       = p("mock:logic",         "Logic");
        Page philosophy  = p("mock:philosophy",    "Philosophy");
        Page calculus    = p("mock:calculus",      "Calculus");
        Page thermo      = p("mock:thermodynamics","Thermodynamics");
        Page optics      = p("mock:optics",        "Optics");
        Page chemistry   = p("mock:chemistry",     "Chemistry");
        Page biology     = p("mock:biology",       "Biology");
        Page statistics  = p("mock:statistics",    "Statistics");
        Page probability = p("mock:probability",   "Probability");
        Page geometry    = p("mock:geometry",      "Geometry");
        Page algebra     = p("mock:algebra",       "Algebra");
        Page cs          = p("mock:cs",            "Computer Science");

        Map<String, Page> pages = new LinkedHashMap<>();
        for (Page pg : List.of(physics, math, quantum, relativity, logic, philosophy,
                               calculus, thermo, optics, chemistry, biology,
                               statistics, probability, geometry, algebra, cs)) {
            pages.put(pg.id(), pg);
        }
        PAGES = Collections.unmodifiableMap(pages);

        // --- links ---
        Map<String, List<Page>> links = new LinkedHashMap<>();
        links.put(physics.id(),     List.of(math, quantum, relativity, thermo, optics));
        links.put(math.id(),        List.of(physics, logic, calculus, statistics, algebra, geometry));
        links.put(quantum.id(),     List.of(physics, relativity, optics));
        links.put(relativity.id(),  List.of(physics, math));
        links.put(logic.id(),       List.of(math, philosophy, cs, algebra));
        links.put(philosophy.id(),  List.of(logic));
        links.put(calculus.id(),    List.of(math, physics, statistics));
        links.put(thermo.id(),      List.of(physics, chemistry, math));
        links.put(optics.id(),      List.of(physics, quantum));
        links.put(chemistry.id(),   List.of(physics, math, biology));
        links.put(biology.id(),     List.of(chemistry, statistics));
        links.put(statistics.id(),  List.of(math, probability, biology, calculus));
        links.put(probability.id(), List.of(math, statistics));
        links.put(geometry.id(),    List.of(math, physics));
        links.put(algebra.id(),     List.of(math, logic, cs));
        links.put(cs.id(),          List.of(math, logic, algebra));
        LINKS = Collections.unmodifiableMap(links);

        // --- bodies ---
        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put(physics.id(),
            "<h1>Physics</h1><p>Physics is the natural science that studies matter, energy, and their interactions. " +
            "Key branches include <a href=\"mock:quantum\">Quantum Mechanics</a>, " +
            "<a href=\"mock:relativity\">Relativity</a>, " +
            "<a href=\"mock:thermodynamics\">Thermodynamics</a>, and " +
            "<a href=\"mock:optics\">Optics</a>. " +
            "Underpinned by <a href=\"mock:math\">Mathematics</a>.</p>");
        bodies.put(math.id(),
            "<h1>Mathematics</h1><p>The study of numbers, structures, and patterns. " +
            "Core areas: <a href=\"mock:logic\">Logic</a>, " +
            "<a href=\"mock:calculus\">Calculus</a>, " +
            "<a href=\"mock:algebra\">Algebra</a>, " +
            "<a href=\"mock:geometry\">Geometry</a>, and " +
            "<a href=\"mock:statistics\">Statistics</a>. " +
            "Applied throughout <a href=\"mock:physics\">Physics</a>.</p>");
        bodies.put(quantum.id(),
            "<h1>Quantum Mechanics</h1><p>Describes nature at atomic and subatomic scales. " +
            "Related to <a href=\"mock:physics\">Physics</a>, " +
            "<a href=\"mock:relativity\">Relativity</a>, and " +
            "<a href=\"mock:optics\">Optics</a>.</p>");
        bodies.put(relativity.id(),
            "<h1>Relativity</h1><p>Einstein's special and general theories describe space, time, and gravity. " +
            "See also: <a href=\"mock:physics\">Physics</a>, " +
            "<a href=\"mock:math\">Mathematics</a>.</p>");
        bodies.put(logic.id(),
            "<h1>Logic</h1><p>The study of valid inference and formal reasoning. " +
            "Related: <a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:philosophy\">Philosophy</a>, " +
            "<a href=\"mock:algebra\">Algebra</a>, " +
            "<a href=\"mock:cs\">Computer Science</a>.</p>");
        bodies.put(philosophy.id(),
            "<h1>Philosophy</h1><p>Examines fundamental questions about existence, knowledge, and ethics. " +
            "Grounded in <a href=\"mock:logic\">Logic</a>.</p>");
        bodies.put(calculus.id(),
            "<h1>Calculus</h1><p>The mathematical study of continuous change — differentiation and integration. " +
            "Related: <a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:physics\">Physics</a>, " +
            "<a href=\"mock:statistics\">Statistics</a>.</p>");
        bodies.put(thermo.id(),
            "<h1>Thermodynamics</h1><p>Studies heat, work, temperature, and energy transfer. " +
            "Related: <a href=\"mock:physics\">Physics</a>, " +
            "<a href=\"mock:chemistry\">Chemistry</a>, " +
            "<a href=\"mock:math\">Mathematics</a>.</p>");
        bodies.put(optics.id(),
            "<h1>Optics</h1><p>The branch of physics studying light and its interactions with matter. " +
            "Related: <a href=\"mock:physics\">Physics</a>, " +
            "<a href=\"mock:quantum\">Quantum Mechanics</a>.</p>");
        bodies.put(chemistry.id(),
            "<h1>Chemistry</h1><p>Studies composition, structure, and reactions of matter. " +
            "Related: <a href=\"mock:physics\">Physics</a>, " +
            "<a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:biology\">Biology</a>.</p>");
        bodies.put(biology.id(),
            "<h1>Biology</h1><p>The science of living organisms and life processes. " +
            "Uses <a href=\"mock:chemistry\">Chemistry</a> and " +
            "<a href=\"mock:statistics\">Statistics</a>.</p>");
        bodies.put(statistics.id(),
            "<h1>Statistics</h1><p>The science of collecting, analysing, and interpreting data. " +
            "Rooted in <a href=\"mock:math\">Mathematics</a> and " +
            "<a href=\"mock:probability\">Probability</a>. " +
            "Applied in <a href=\"mock:biology\">Biology</a> and " +
            "<a href=\"mock:calculus\">Calculus</a>.</p>");
        bodies.put(probability.id(),
            "<h1>Probability</h1><p>Quantifies uncertainty and likelihood of events. " +
            "Related: <a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:statistics\">Statistics</a>.</p>");
        bodies.put(geometry.id(),
            "<h1>Geometry</h1><p>Studies shapes, sizes, and properties of figures and spaces. " +
            "Related: <a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:physics\">Physics</a>.</p>");
        bodies.put(algebra.id(),
            "<h1>Algebra</h1><p>Studies symbols and the rules for manipulating them. " +
            "Related: <a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:logic\">Logic</a>, " +
            "<a href=\"mock:cs\">Computer Science</a>.</p>");
        bodies.put(cs.id(),
            "<h1>Computer Science</h1><p>The study of computation, algorithms, and information processing. " +
            "Grounded in <a href=\"mock:math\">Mathematics</a>, " +
            "<a href=\"mock:logic\">Logic</a>, and " +
            "<a href=\"mock:algebra\">Algebra</a>.</p>");
        BODIES = Collections.unmodifiableMap(bodies);
    }

    private static Page p(String id, String title) {
        return new Page(id, title);
    }

    @Override
    public Page defaultPage() {
        return PAGES.get("mock:physics");
    }

    @Override
    public String fetchBody(String id) throws PageNotFoundException {
        String body = BODIES.get(id);
        if (body == null) throw new PageNotFoundException(id);
        return body;
    }

    @Override
    public List<Page> fetchLinks(String id) throws PageNotFoundException {
        List<Page> links = LINKS.get(id);
        if (links == null) throw new PageNotFoundException(id);
        return links;
    }
}
