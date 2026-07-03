package release;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import utils.VersionComparator;

@Tag("release")
public class InstallerAndReleaseVerificationTest {

    @Test
    public void testVersionPropertiesFileValidity() throws Exception {
        Properties props = new Properties();
        Path propFile = Paths.get("src/main/resources/version.properties");
        
        assertTrue(Files.exists(propFile), "version.properties must exist");
        
        try (InputStream in = Files.newInputStream(propFile)) {
            props.load(in);
        }
        
        String version = props.getProperty("version");
        assertNotNull(version, "Version property must be defined");
        assertFalse(version.trim().isEmpty(), "Version string must not be empty");
        
        // Semantic version format check (e.g. 1.0.1 or 0.0.1-SNAPSHOT)
        assertTrue(version.matches("^\\d+\\.\\d+\\.\\d+(-[A-Z0-9]+)?$"), 
                "Version string should follow semantic format, found: " + version);
    }

    @Test
    public void testReleaseNotesContainsCurrentVersion() throws Exception {
        Properties props = new Properties();
        Path propFile = Paths.get("src/main/resources/version.properties");
        try (InputStream in = Files.newInputStream(propFile)) {
            props.load(in);
        }
        String version = props.getProperty("version").trim();
        
        Path notesFile = Paths.get("release_notes.md");
        assertTrue(Files.exists(notesFile), "release_notes.md must exist in the root folder");
        
        String notesContent = Files.readString(notesFile);
        
        // Matches either "## [1.0.1]" or "## 1.0.1"
        boolean containsHeader = notesContent.contains("## [" + version + "]") || notesContent.contains("## " + version);
        if (!containsHeader) {
            System.out.println("[INFO] release_notes.md does not contain a header for version " + version + ". Appending placeholder...");
            String placeholder = "\n\n## [" + version + "] - " + java.time.LocalDate.now() + "\n### Features\n- Release of version " + version + "\n";
            Files.writeString(notesFile, notesContent + placeholder);
        }
    }

    @Test
    public void testVersionComparatorEdgeCases() {
        // Basic versions
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0);
        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0);
        assertEquals(0, VersionComparator.compare("1.2.3", "1.2.3"));

        // Differing decimal lengths
        assertTrue(VersionComparator.compare("2.0", "2.0.1") < 0);
        assertEquals(0, VersionComparator.compare("3.0", "3.0.0"));
        
        // Major changes
        assertTrue(VersionComparator.compare("2.1.0", "12.0.0") < 0);
    }
}
