package br.com.ottimizza.robo.mapeador;

import br.com.ottimizza.robo.discovery.ListadorSubpastas;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listagem de subpastas lembrada durante a sessao do mapeador.
 *
 * <p>O analista ajusta uma regra, a tela pede uma nova previa, e a varredura percorre de novo
 * quase as mesmas pastas. Num compartilhamento de rede cada listagem e uma ida ao servidor; sem
 * memoria, cada clique custaria a varredura inteira. O preco e nao ver uma pasta criada depois que
 * o ramo foi listado - a tela tem um botao "Atualizar pastas" que chama {@link #esquecer()}.
 *
 * <p>So o mapeador usa isto. O robo em producao lista sempre do disco: la, uma lista velha
 * significaria deixar de enviar guias.
 */
final class ListagemMemorizada implements ListadorSubpastas {

    private final Map<Path, List<Path>> listagens = new ConcurrentHashMap<>();
    private final Set<Path> semAcesso = ConcurrentHashMap.newKeySet();

    @Override
    public List<Path> listar(Path pasta) {
        List<Path> pronta = listagens.get(pasta);
        if (pronta != null) {
            return pronta;
        }
        // Fora de computeIfAbsent de proposito: ele seguraria o lock do bin durante uma listagem
        // de rede lenta. Duas threads listando a mesma pasta ao mesmo tempo so desperdicam uma ida.
        AtomicBoolean falhou = new AtomicBoolean();
        List<Path> nova = List.copyOf(
                ListadorSubpastas.doDisco((mensagem, causa) -> falhou.set(true)).listar(pasta));
        if (falhou.get()) {
            semAcesso.add(pasta);
        }
        List<Path> anterior = listagens.putIfAbsent(pasta, nova);
        return anterior != null ? anterior : nova;
    }

    /** Se a ultima listagem desta pasta falhou (sem permissao, pasta apagada, rede fora). */
    boolean semAcesso(Path pasta) {
        return semAcesso.contains(pasta);
    }

    void esquecer() {
        listagens.clear();
        semAcesso.clear();
    }
}
