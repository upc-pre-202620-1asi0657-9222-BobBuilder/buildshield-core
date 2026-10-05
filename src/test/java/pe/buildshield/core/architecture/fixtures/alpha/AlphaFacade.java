package pe.buildshield.core.architecture.fixtures.alpha;

import pe.buildshield.core.architecture.fixtures.alpha.infrastructure.AlphaRepository;

/** Clase de ejemplo: fachada del módulo alpha (acceso permitido a su propio repositorio). */
public class AlphaFacade {

    private final AlphaRepository repository = new AlphaRepository();

    public String find() {
        return repository.find();
    }
}
