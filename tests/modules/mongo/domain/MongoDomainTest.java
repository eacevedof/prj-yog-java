package modules.mongo.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.mongo.domain.entities.MongoShellCommandEntity;
import modules.mongo.domain.services.MongoDangerousCommandDetector;
import modules.mongo.domain.services.MongoShellParser;

/**
 * La consola de MongoDB sin servidor: como se parte una orden tipo mongosh y que se considera peligroso.
 */
final class MongoDomainTest extends AbstractTestCase {

    private final MongoShellParser mongoShellParser = MongoShellParser.getInstance();
    private final MongoDangerousCommandDetector detector = MongoDangerousCommandDetector.getInstance();

    @Test
    void testCollectionMethodsWithArgumentsAndModifiers() {
        MongoShellCommandEntity find = mongoShellParser.getCommand("db.users.find({age: {$gt: 18}, name: \"a, b)\"}, {name: 1}).sort({age: -1}).limit(10);");
        MongoShellCommandEntity getCollection = mongoShellParser.getCommand("db.getCollection('my-coll').countDocuments()");
        MongoShellCommandEntity update = mongoShellParser.getCommand("db.users.updateMany({}, {$set: {tags: [1, 2]}})");

        assertEquals(MongoShellCommandEntity.COLLECTION_METHOD, find.type());
        assertEquals("users", find.name());
        assertEquals("find", find.method());
        assertEquals(List.of("{age: {$gt: 18}, name: \"a, b)\"}", "{name: 1}"), find.arguments());
        assertEquals(List.of(List.of("sort", "{age: -1}"), List.of("limit", "10")), find.modifiers());
        assertEquals("my-coll", getCollection.name());
        assertEquals(List.of(), getCollection.arguments());
        assertEquals(List.of("{}", "{$set: {tags: [1, 2]}}"), update.arguments());
    }

    @Test
    void testShellCommandsAndRunCommand() {
        assertEquals(MongoShellCommandEntity.SHOW_DATABASES, mongoShellParser.getCommand("show  dbs").type());
        assertEquals(MongoShellCommandEntity.SHOW_COLLECTIONS, mongoShellParser.getCommand("show collections;").type());
        assertEquals("shop", mongoShellParser.getCommand("use shop").name());
        assertEquals(List.of("{ping: 1}"), mongoShellParser.getCommand("db.runCommand({ping: 1})").arguments());
        assertEquals(List.of("{buildInfo: 1}"), mongoShellParser.getCommand("{buildInfo: 1}").arguments());
        assertEquals(MongoShellCommandEntity.DROP_DATABASE, mongoShellParser.getCommand("db.dropDatabase()").type());
        assertThrows(IllegalArgumentException.class, () -> mongoShellParser.getCommand("db.users.find({a: 1}"));
        assertThrows(IllegalArgumentException.class, () -> mongoShellParser.getCommand("select * from users"));
    }

    @Test
    void testDangerousCommands() {
        assertTrue(detector.isDangerous(mongoShellParser.getCommand("db.users.deleteMany({})")));
        assertTrue(detector.isDangerous(mongoShellParser.getCommand("db.users.drop()")));
        assertTrue(detector.isDangerous(mongoShellParser.getCommand("db.dropDatabase()")));
        assertTrue(detector.isDangerous(mongoShellParser.getCommand("{\"drop\": \"users\"}")));
        assertFalse(detector.isDangerous(mongoShellParser.getCommand("db.users.find({})")));
        assertFalse(detector.isDangerous(mongoShellParser.getCommand("db.users.insertOne({a: 1})")));
        assertFalse(detector.isDangerous(mongoShellParser.getCommand("{ping: 1}")));
    }
}
