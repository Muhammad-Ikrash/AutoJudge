package autojudge.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {

    @Test
    void testFromFileName() {
        assertEquals(Language.CPP, Language.fromFileName("solution.cpp"));
        assertEquals(Language.CPP, Language.fromFileName("solution.cc"));
        assertEquals(Language.CPP, Language.fromFileName("solution.cxx"));
        assertEquals(Language.CPP, Language.fromFileName("header.hpp"));
        assertEquals(Language.C, Language.fromFileName("main.c"));
        assertEquals(Language.JAVA, Language.fromFileName("Main.java"));
        assertEquals(Language.PYTHON, Language.fromFileName("script.py"));
        assertNull(Language.fromFileName("notes.txt"));
        assertNull(Language.fromFileName(null));
    }

    @Test
    void testIsSupportedSourceFile() {
        assertTrue(Language.isSupportedSourceFile("Main.java"));
        assertTrue(Language.isSupportedSourceFile("solution.cpp"));
        assertFalse(Language.isSupportedSourceFile("data.csv"));
    }
}
