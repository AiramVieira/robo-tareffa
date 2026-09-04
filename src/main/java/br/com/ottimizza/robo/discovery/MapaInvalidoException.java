package br.com.ottimizza.robo.discovery;

/**
 * Problema de sintaxe em {@code mapa-pastas.txt} (secao 6.6 do readme). A mensagem descreve o
 * erro sem o numero da linha — quem conhece a linha e o {@link MapaPastasLoader}, que a prefixa.
 */
public final class MapaInvalidoException extends Exception {

    private static final long serialVersionUID = 1L;

    public MapaInvalidoException(String motivo) {
        super(motivo);
    }
}
