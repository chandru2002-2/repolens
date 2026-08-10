package io.repolens.parse.engine;

import ch.usi.si.seart.treesitter.Capture;
import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.LibraryLoader;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Point;
import ch.usi.si.seart.treesitter.Query;
import ch.usi.si.seart.treesitter.QueryCursor;
import ch.usi.si.seart.treesitter.QueryMatch;
import ch.usi.si.seart.treesitter.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tree-sitter engine backed by seart java-tree-sitter.
 * May be unavailable on platforms without a compatible native binary (e.g. arm64 with x86_64-only jar).
 */
public final class SeartTreeSitterEngine implements SyntaxQueryEngine {

    private static final Logger LOGGER = Logger.getLogger(SeartTreeSitterEngine.class.getName());

    private final boolean available;

    public SeartTreeSitterEngine() {
        this.available = probe();
    }

    private static boolean probe() {
        try {
            LibraryLoader.load();
            try (Parser parser = Parser.getFor(Language.JAVA);
                 Tree tree = parser.parse("class A {}")) {
                return tree.getRootNode() != null;
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.INFO, "Tree-sitter natives unavailable; fallback extractor will be used: {0}",
                    ex.toString());
            return false;
        }
    }

    @Override
    public String id() {
        return "tree-sitter-seart";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public List<SyntaxCapture> query(String treeSitterLanguage, String source, String queryText) {
        if (!available) {
            throw new IllegalStateException("Tree-sitter engine is not available on this platform");
        }
        Language language = Language.valueOf(treeSitterLanguage.toUpperCase(Locale.ROOT));
        List<SyntaxCapture> captures = new ArrayList<>();
        try (Parser parser = Parser.getFor(language);
             Tree tree = parser.parse(source);
             Query query = Query.getFor(language, queryText);
             QueryCursor cursor = tree.getRootNode().walk(query)) {
            for (QueryMatch match : cursor) {
                for (Map.Entry<Capture, java.util.Collection<Node>> entry : match.getCaptures().entrySet()) {
                    String name = entry.getKey().getName();
                    for (Node node : entry.getValue()) {
                        Point start = node.getStartPoint();
                        Point end = node.getEndPoint();
                        captures.add(new SyntaxCapture(
                                name,
                                node.getContent() == null ? "" : node.getContent(),
                                start.getRow() + 1,
                                start.getColumn(),
                                end.getRow() + 1,
                                end.getColumn()
                        ));
                    }
                }
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Tree-sitter query failed for " + treeSitterLanguage, ex);
        }
        return captures;
    }
}
