package br.edu.uniesp.assistencia.internal.ordemservico.controller;

import br.edu.uniesp.assistencia.config.exception.GlobalExceptionHandler;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoResponse;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.StatusOrdemServico;
import br.edu.uniesp.assistencia.internal.ordemservico.service.OrdemServicoService;
import br.edu.uniesp.assistencia.shared.exception.RecursoNaoEncontradoException;
import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrdemServicoControllerTest {

    @Mock
    private OrdemServicoService ordemServicoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrdemServicoController controller = new OrdemServicoController(ordemServicoService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void cenario1_deveRetornar201NaAberturaComSucesso() throws Exception {
        LocalDateTime abertura = LocalDateTime.of(2026, 10, 15, 20, 30);
        when(ordemServicoService.abrirOrdemServico(any())).thenReturn(
                new CriarOrdemServicoResponse(1L, "OS-20261015-9F2B", StatusOrdemServico.RECEBIDA, abertura)
        );

        mockMvc.perform(post("/ordens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": 1,
                                  "equipamentoId": 2,
                                  "descricaoProblema": "Notebook não liga após queda de energia elétrica."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.numeroProtocolo").value("OS-20261015-9F2B"))
                .andExpect(jsonPath("$.status").value("RECEBIDA"))
                .andExpect(jsonPath("$.dataAbertura").value("2026-10-15T20:30:00"));
    }

    @Test
    void cenario2_deveRetornar400ComProblemDetailParaCamposInvalidos() throws Exception {
        mockMvc.perform(post("/ordens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": null,
                                  "equipamentoId": null,
                                  "descricaoProblema": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Dados de requisição inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erros.clienteId").exists())
                .andExpect(jsonPath("$.erros.equipamentoId").exists())
                .andExpect(jsonPath("$.erros.descricaoProblema").exists());
    }

    @Test
    void cenario3_deveRetornar404ComProblemDetailQuandoClienteNaoExiste() throws Exception {
        when(ordemServicoService.abrirOrdemServico(any()))
                .thenThrow(new RecursoNaoEncontradoException("Cliente", 99L));

        mockMvc.perform(post("/ordens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadValido()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.recurso").value("Cliente"))
                .andExpect(jsonPath("$.identificador").value(99));
    }

    @Test
    void cenario4_deveRetornar422ParaClienteInativo() throws Exception {
        when(ordemServicoService.abrirOrdemServico(any()))
                .thenThrow(new RegraNegocioException("Não é permitido abrir Ordem de Serviço para um cliente inativo."));

        mockMvc.perform(post("/ordens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadValido()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Não é permitido abrir Ordem de Serviço para um cliente inativo."));
    }

    @Test
    void cenario5_deveRetornar422ParaEquipamentoDeOutroCliente() throws Exception {
        when(ordemServicoService.abrirOrdemServico(any()))
                .thenThrow(new RegraNegocioException("O equipamento informado não pertence ao cliente indicado."));

        mockMvc.perform(post("/ordens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadValido()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Regra de negócio violada"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("O equipamento informado não pertence ao cliente indicado."));
    }

    private String payloadValido() {
        return """
                {
                  "clienteId": 1,
                  "equipamentoId": 2,
                  "descricaoProblema": "Notebook não liga após queda de energia elétrica."
                }
                """;
    }
}
