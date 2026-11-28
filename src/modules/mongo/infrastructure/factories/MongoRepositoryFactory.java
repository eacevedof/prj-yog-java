package modules.mongo.infrastructure.factories;

import modules.connections.infrastructure.factories.MongoSessionFactory;

import modules.mongo.infrastructure.repositories.AbstractMongoReaderRepository;
import modules.mongo.infrastructure.repositories.AbstractMongoShellRepository;
import modules.mongo.infrastructure.repositories.AbstractMongoWriterRepository;
import modules.mongo.infrastructure.repositories.MongoReaderHttpTunnelRepository;
import modules.mongo.infrastructure.repositories.MongoReaderRepository;
import modules.mongo.infrastructure.repositories.MongoShellHttpTunnelRepository;
import modules.mongo.infrastructure.repositories.MongoShellRepository;
import modules.mongo.infrastructure.repositories.MongoWriterHttpTunnelRepository;
import modules.mongo.infrastructure.repositories.MongoWriterRepository;

/**
 * Los repositorios de MongoDB de una conexion, sobre la sesion de esa clave: por el driver (directo o SSH) o, si la
 * conexion va por el tunel HTTP, por el tunel. Los servicios piden aqui y no saben por donde va.
 */
public final class MongoRepositoryFactory {

    private MongoRepositoryFactory() {
    }

    public static MongoRepositoryFactory getInstance() {
        return new MongoRepositoryFactory();
    }

    /**
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AbstractMongoReaderRepository getReaderRepository(String connectionUuid, String sessionKey) {
        MongoSessionFactory mongoSessionFactory = MongoSessionFactory.getInstance();
        if (mongoSessionFactory.isHttpTunnel(connectionUuid)) {
            return MongoReaderHttpTunnelRepository.getInstance(mongoSessionFactory.getTunnelClient(connectionUuid, sessionKey));
        }
        return MongoReaderRepository.getInstance(mongoSessionFactory.getSession(connectionUuid, sessionKey));
    }

    /**
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AbstractMongoWriterRepository getWriterRepository(String connectionUuid, String sessionKey) {
        MongoSessionFactory mongoSessionFactory = MongoSessionFactory.getInstance();
        if (mongoSessionFactory.isHttpTunnel(connectionUuid)) {
            return MongoWriterHttpTunnelRepository.getInstance(mongoSessionFactory.getTunnelClient(connectionUuid, sessionKey));
        }
        return MongoWriterRepository.getInstance(mongoSessionFactory.getSession(connectionUuid, sessionKey));
    }

    /**
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AbstractMongoShellRepository getShellRepository(String connectionUuid, String sessionKey) {
        MongoSessionFactory mongoSessionFactory = MongoSessionFactory.getInstance();
        if (mongoSessionFactory.isHttpTunnel(connectionUuid)) {
            return MongoShellHttpTunnelRepository.getInstance(mongoSessionFactory.getTunnelClient(connectionUuid, sessionKey));
        }
        return MongoShellRepository.getInstance(mongoSessionFactory.getSession(connectionUuid, sessionKey));
    }
}
