package br.edu.uniesp.assistencia.internal.ordemservico.service;

import br.edu.uniesp.assistencia.internal.cliente.entity.ClienteEntity;
import br.edu.uniesp.assistencia.internal.cliente.repository.ClienteRepository;
import br.edu.uniesp.assistencia.internal.equipamento.entity.EquipamentoEntity;
import br.edu.uniesp.assistencia.internal.equipamento.repository.EquipamentoRepository;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoRequest;
import br.edu.uniesp.assistencia.internal.ordemservico.dto.CriarOrdemServicoResponse;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.OrdemServicoEntity;
import br.edu.uniesp.assistencia.internal.ordemservico.entity.StatusOrdemServico;
import br.edu.uniesp.assistencia.internal.ordemservico.repository.OrdemServicoRepository;
import br.edu.uniesp.assistencia.shared.exception.RecursoNaoEncontradoException;
import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdemServicoServiceTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private EquipamentoRepository equipamentoRepository;

    private OrdemServicoService service;

    @BeforeEach
    void setUp() {
        service = new OrdemServicoService(ordemServicoRepository, clienteRepository, equipamentoRepository);
    }

    @Test
    void deveAbrirOrdemComStatusRecebidaProtocoloDataEHistoricoInicial() {
        ClienteEntity cliente = new ClienteEntity("Cliente Ativo", "12345678901", "cliente@email.com");
        EquipamentoEntity equipamento = new EquipamentoEntity("Notebook", "Marca", "Modelo", "ABC123");
        equipamento.associarCliente(cliente);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(equipamentoRepository.findById(2L)).thenReturn(Optional.of(equipamento));
        when(ordemServicoRepository.existsByNumeroProtocolo(anyString())).thenReturn(false);
        when(ordemServicoRepository.save(any(OrdemServicoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CriarOrdemServicoResponse response = service.abrirOrdemServico(
                new CriarOrdemServicoRequest(1L, 2L, "Notebook não liga")
        );

        String dataHoje = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertEquals(StatusOrdemServico.RECEBIDA, response.status());
        assertNotNull(response.dataAbertura());
        assertTrue(response.numeroProtocolo().matches("OS-" + dataHoje + "-[0-9A-F]{4}"));

        ArgumentCaptor<OrdemServicoEntity> captor = ArgumentCaptor.forClass(OrdemServicoEntity.class);
        verify(ordemServicoRepository).save(captor.capture());

        OrdemServicoEntity salva = captor.getValue();
        assertEquals(1, salva.getHistorico().size());
        assertEquals(StatusOrdemServico.RECEBIDA, salva.getHistorico().getFirst().getNovoStatus());
        assertEquals("SISTEMA", salva.getHistorico().getFirst().getResponsavel());
        assertEquals("Abertura da ordem de serviço.", salva.getHistorico().getFirst().getObservacao());
    }

    @Test
    void deveRetornarErroQuandoClienteNaoExiste() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException exception = assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.abrirOrdemServico(new CriarOrdemServicoRequest(99L, 2L, "Defeito"))
        );

        assertEquals("Cliente", exception.getRecurso());
        verifyNoInteractions(equipamentoRepository);
    }

    @Test
    void deveRetornarErroQuandoEquipamentoNaoExiste() {
        ClienteEntity cliente = new ClienteEntity("Cliente", "12345678901", "cliente@email.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(equipamentoRepository.findById(99L)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException exception = assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.abrirOrdemServico(new CriarOrdemServicoRequest(1L, 99L, "Defeito"))
        );

        assertEquals("Equipamento", exception.getRecurso());
    }

    @Test
    void deveBloquearClienteInativo() {
        ClienteEntity cliente = new ClienteEntity("Cliente", "12345678901", "cliente@email.com");
        cliente.inativar();
        EquipamentoEntity equipamento = new EquipamentoEntity("Notebook", "Marca", "Modelo", "ABC123");
        equipamento.associarCliente(cliente);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(equipamentoRepository.findById(2L)).thenReturn(Optional.of(equipamento));
        when(ordemServicoRepository.existsByNumeroProtocolo(anyString())).thenReturn(false);

        RegraNegocioException exception = assertThrows(
                RegraNegocioException.class,
                () -> service.abrirOrdemServico(new CriarOrdemServicoRequest(1L, 2L, "Defeito"))
        );

        assertEquals("Não é permitido abrir Ordem de Serviço para um cliente inativo.", exception.getMessage());
        verify(ordemServicoRepository, never()).save(any());
    }

    @Test
    void deveBloquearEquipamentoDeOutroCliente() {
        ClienteEntity clienteInformado = new ClienteEntity("Cliente A", "12345678901", "a@email.com");
        ClienteEntity outroCliente = new ClienteEntity("Cliente B", "10987654321", "b@email.com");
        EquipamentoEntity equipamento = new EquipamentoEntity("Notebook", "Marca", "Modelo", "ABC123");
        equipamento.associarCliente(outroCliente);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteInformado));
        when(equipamentoRepository.findById(2L)).thenReturn(Optional.of(equipamento));
        when(ordemServicoRepository.existsByNumeroProtocolo(anyString())).thenReturn(false);

        RegraNegocioException exception = assertThrows(
                RegraNegocioException.class,
                () -> service.abrirOrdemServico(new CriarOrdemServicoRequest(1L, 2L, "Defeito"))
        );

        assertEquals("O equipamento informado não pertence ao cliente indicado.", exception.getMessage());
        verify(ordemServicoRepository, never()).save(any());
    }

    @Test
    void deveTentarOutroProtocoloQuandoHouverColisao() {
        ClienteEntity cliente = new ClienteEntity("Cliente", "12345678901", "cliente@email.com");
        EquipamentoEntity equipamento = new EquipamentoEntity("Notebook", "Marca", "Modelo", "ABC123");
        equipamento.associarCliente(cliente);

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(equipamentoRepository.findById(2L)).thenReturn(Optional.of(equipamento));
        when(ordemServicoRepository.existsByNumeroProtocolo(anyString())).thenReturn(true, false);
        when(ordemServicoRepository.save(any(OrdemServicoEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.abrirOrdemServico(new CriarOrdemServicoRequest(1L, 2L, "Defeito"));

        verify(ordemServicoRepository, times(2)).existsByNumeroProtocolo(anyString());
    }
}
