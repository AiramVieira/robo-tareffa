package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizacaoRuleTest {

    @Test
    void parseiaExemploDaSecao63() {
        Optional<List<CustomizacaoRule>> regras = CustomizacaoRule.parseAll("1:>!INATIVO;1:+CONSOLIDADO");

        assertTrue(regras.isPresent());
        assertEquals(2, regras.get().size());

        // Nota: o readme usa "EMPRESA INATIVA LTDA" na ilustracao da arvore mas o termo da
        // regra e "INATIVO" (masculino) - aqui testamos com o nome que de fato contem o termo.
        CustomizacaoRule bloqueiaInativo = regras.get().get(0);
        assertEquals(1, bloqueiaInativo.getNivel());
        assertEquals(CustomizacaoRule.Operador.FILTRA, bloqueiaInativo.getOperador());
        assertFalse(bloqueiaInativo.disparaPara("EMPRESA INATIVO LTDA"));
        assertTrue(bloqueiaInativo.disparaPara("EMPRESA ATIVA LTDA"));

        CustomizacaoRule aumentaConsolidado = regras.get().get(1);
        assertEquals(1, aumentaConsolidado.getNivel());
        assertEquals(CustomizacaoRule.Operador.AUMENTA_PROFUNDIDADE, aumentaConsolidado.getOperador());
        assertTrue(aumentaConsolidado.disparaPara("EMPRESA CONSOLIDADO LTDA"));
        assertFalse(aumentaConsolidado.disparaPara("EMPRESA ATIVA LTDA"));
    }

    @Test
    void comparacaoIgnoraCaixaEAcentuacao() {
        Optional<List<CustomizacaoRule>> regras = CustomizacaoRule.parseAll("1:>Filial");
        assertTrue(regras.isPresent());
        assertTrue(regras.get().get(0).disparaPara("FILIAL SÃO PAULO"));
        assertTrue(regras.get().get(0).disparaPara("filial-sao-paulo"));
    }

    @Test
    void nivelNaoNumericoMarcaConfiguracaoComoInvalida() {
        assertTrue(CustomizacaoRule.parseAll("abc:>Filial").isEmpty());
    }

    @Test
    void operadorDesconhecidoMarcaConfiguracaoComoInvalida() {
        assertTrue(CustomizacaoRule.parseAll("1:?Filial").isEmpty());
    }

    @Test
    void customizacaoVaziaResultaEmListaVaziaValida() {
        Optional<List<CustomizacaoRule>> regras = CustomizacaoRule.parseAll("");
        assertTrue(regras.isPresent());
        assertTrue(regras.get().isEmpty());
    }
}
