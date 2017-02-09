package no.balder.spiralis.payload;

import no.balder.spiralis.testutil.DummyFiles;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.testng.Assert.*;

/**
 * @author steinar
 *         Date: 06.02.2017
 *         Time: 19.06
 */
public class PayloadPathUtilTest {


    private Path rootInboundSampleFiles;

    @BeforeMethod
    public void setUp() throws Exception {
        rootInboundSampleFiles = DummyFiles.createInboundDummyFilesInRootWithSubdirs();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        DummyFiles.removeAll(rootInboundSampleFiles);
    }

    @Test
    public void testClassify() throws Exception {

        Files.createTempFile(rootInboundSampleFiles, "test", ".rubbish");
        final DirectoryStream<Path> stream = Files.newDirectoryStream(rootInboundSampleFiles);

        int unknown = 0;
        int receipt = 0;
        int payload = 0;
        int rem = 0;

        for (Path path : stream) {

                switch (PayloadPathUtil.classify(path)) {
                    case AS2_RECEIPT:
                        receipt++;
                        break;
                    case PAYLOAD:
                        payload++;
                        break;
                    case REM_EVIDENCE:
                        rem++;
                        break;
                    case UNKNOWN:
                        unknown++;
                        break;
                    default:
                        throw new IllegalStateException("There is a bug in your test for path " + path);
                }
            }

        assertEquals(unknown, 1, "Did not manage to classify the unknonw");
        assertEquals(receipt, 1, "Receipt not classified");
        assertEquals(payload, 1, "Payload not classified");
        assertEquals(rem, 1, "REM not classified");

    }


    @Test
    public void testPathManipulation() throws Exception {

        // Creates root dir in tmp, adding two extra path segments
        final Path rootDir = DummyFiles.createInboundDummyFilesInRootWithSubdirs("IN","2017");
        final Path inboundPath = rootDir.resolve("IN");
        final Path archive =    rootDir.resolve("PROCESSED");


        final List<Path> paths = DummyFiles.locatePayloadFilesIn(rootDir);
        assertTrue(paths.size() > 0);
        final Path payload = paths.get(0);

        //       /var/folders/1x/_l5zmgh978j2k7l43gbl60b00000gn/T/test6258362728362175884
        System.out.println("root   :" + rootDir);
        System.out.println("inbound:" + inboundPath);
        System.out.println("payload:" + payload);
        System.out.println("Archive:" + archive);

        final Path newPathFor = PayloadPathUtil.createNewPathFor(inboundPath, payload, archive);
        System.out.println(newPathFor);

        // Compares the last segments
        final Path relativize = inboundPath.relativize(payload);
        final Path relativize1 = archive.relativize(newPathFor);
        System.out.println(relativize + " = " + relativize1);
        assertEquals(relativize, relativize1);

        Files.createDirectories(newPathFor.getParent());
        Files.move(payload, newPathFor);
        Files.exists(newPathFor);
        System.out.println("Moved " + payload);
        System.out.println("to    " + newPathFor);

        DummyFiles.removeAll(rootDir);
    }

    @Test
    public void testMovePath() {
        final Path srcSegment           = Paths.get("/root/SRC");
        final Path destinationSegment   = Paths.get("/root/DST");
        final Path partialSrcPath       = Paths.get("x","foo.bar");
        final Path completeSrcPath      = srcSegment.resolve(partialSrcPath);

        final Path newPathFor = PayloadPathUtil.createNewPathFor(srcSegment, completeSrcPath, destinationSegment);
        final Path expectedPath = Paths.get("root", "DST", "x", "foo.bar");
        assertEquals(newPathFor, expectedPath);

        final Path relativize = srcSegment.relativize(completeSrcPath);
        final Path relativize1 = destinationSegment.relativize(newPathFor);
        System.out.println(relativize);
        System.out.println(relativize1);
        assertEquals(relativize, relativize1);
    }

    @Test
    public void testBaseFileName() throws Exception {
        final String s = PayloadPathUtil.fileNameBodyPart(Paths.get("/root", "ding", "dong", "1234-456a-1234-doc.xml"));
        assertEquals(s, "1234-456a-1234");
    }

    @Test
    public void testMoveFilesUsingDetailedMethods() throws Exception {

        final Path rootDir = DummyFiles.createInboundDummyFilesInRootWithSubdirs("IN","ding","dong");
        final Path inboundRoot = rootDir.resolve("IN");

        // Creates sample files in .../IN/ding/dong/
        final List<Path> paths = DummyFiles.locatePayloadFilesIn(inboundRoot);
        assertTrue(paths.size() >= 1);

        // Creates a new temporary destination/archive/target directory
        final Path archive = Files.createTempDirectory("ARC");

        final Path payloadPath = paths.get(0);  // Grabs the payload file

        // Computes path in archive directory...
        final Path newPathFor = PayloadPathUtil.createNewPathFor(inboundRoot, payloadPath, archive);

        // Performs the actual move
        final Path moved = PayloadPathUtil.move(payloadPath, newPathFor);

        assertTrue(moved.toString().contains("ding"));
        
        System.out.println("Moved " + payloadPath + " to " + moved);

        DummyFiles.removeAll(rootDir);
    }

    @Test
    public void moveFile() throws Exception {
        final Path rootDir = DummyFiles.createInboundDummyFilesInRootWithSubdirs("IN","ding","dong");
        final Path inboundRoot = rootDir.resolve("IN");
        final Path archive = rootDir.resolve("ARC");

        final List<Path> paths = DummyFiles.locatePayloadFilesIn(inboundRoot);
        final Path payload = paths.get(0);

        // Attempts to move a file from one directory tree to another with any subdirectory structure and file name intact.
        final Path archived = PayloadPathUtil.moveWithSubdirIntact(inboundRoot, payload, archive);

        assertFalse(Files.exists(payload));
        assertTrue(Files.exists(archived));

    }

    @Test
    public void testInvalidPaths() throws Exception {

        final String tmpdirName = System.getProperty("java.io.tmpdir");

        // this should fail
        try {
            PayloadPathUtil.createNewPathFor(Paths.get(tmpdirName), Paths.get("t/x"), Paths.get(tmpdirName).resolve("ARC"));
            fail("Relative file paths are not allowed");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void payloadPathNotWithinRoot() throws Exception {

        final String tmpdirName = System.getProperty("java.io.tmpdir");

        // this should fail
        try {
            PayloadPathUtil.createNewPathFor(Paths.get(tmpdirName), Paths.get("/dummy/t/x"), Paths.get(tmpdirName).resolve("ARC"));
            fail("Relative file paths are not allowed");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());// expected
        }
    }

    @Test
    public void testPaths() throws Exception {
        final Path root = Paths.get("/tmp", "A");
        assertTrue(root.isAbsolute());

        final Path overlappingPath = root.resolve("B");

        System.out.println(root);
        System.out.println(overlappingPath);
        
        assertTrue(overlappingPath.startsWith(root));

        final int nameCount = root.getNameCount();
        assertEquals(nameCount, 2);
        
        assertFalse(root.startsWith(overlappingPath));

        assertTrue(overlaps(root, overlappingPath));
        assertTrue(overlaps(overlappingPath, root));
        assertTrue(overlaps(root, root));

        assertFalse(overlaps(Paths.get("/tmp/inbound"), Paths.get("/tmp/archive")));
        assertTrue(overlaps(Paths.get("/tmp/inbound"), Paths.get("/tmp/inbound/archive")));
    }

    boolean overlaps(Path p1, Path p2) {
        return PayloadPathUtil.overlaps(p1, p2);
    }
}