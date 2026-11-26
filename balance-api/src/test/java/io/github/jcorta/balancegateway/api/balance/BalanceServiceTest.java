package io.github.jcorta.balancegateway.api.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import io.github.jcorta.balancegateway.api.core.CoreUnavailableException;
import io.github.jcorta.balancegateway.api.core.ResilientCoreBalanceClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final String ACCOUNT = "ACC-1001";
    private static final Instant FRESH = Instant.parse("2026-11-26T14:10:00Z");
    private static final Instant OLD = Instant.parse("2026-11-26T14:03:12.481Z");

    @Mock
    private ResilientCoreBalanceClient core;

    @Mock
    private LastKnownBalanceCache lastKnown;

    private BalanceService service;

    @BeforeEach
    void createService() {
        service = new BalanceService(core, lastKnown);
    }

    @Test
    void returnsCurrentBalanceAndRemembersItWhenCoreAnswers() {
        AccountBalance fromCore = balance(FRESH);
        when(core.fetchBalance(ACCOUNT)).thenReturn(fromCore);

        BalanceResponse response = service.balanceOf(ACCOUNT);

        assertThat(response.stale()).isFalse();
        assertThat(response.asOf()).isEqualTo(FRESH);
        verify(lastKnown).put(fromCore);
        verify(lastKnown, never()).find(any());
    }

    @Test
    void returnsStaleBalanceWithOriginalAsOfWhenCoreIsDown() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(coreDown());
        when(lastKnown.find(ACCOUNT)).thenReturn(Optional.of(balance(OLD)));

        BalanceResponse response = service.balanceOf(ACCOUNT);

        assertThat(response.stale()).isTrue();
        assertThat(response.asOf()).isEqualTo(OLD);
        assertThat(response.balance()).isEqualByComparingTo("152300.75");
        assertThat(response.accountId()).isEqualTo(ACCOUNT);
        assertThat(response.currency()).isEqualTo("ARS");
    }

    @Test
    void failsWhenCoreIsDownAndNoBalanceIsKnown() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(coreDown());
        when(lastKnown.find(ACCOUNT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.balanceOf(ACCOUNT)).isInstanceOf(BalanceUnavailableException.class);
    }

    @Test
    void doesNotRememberStaleBalanceAsIfItWereFresh() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(coreDown());
        when(lastKnown.find(ACCOUNT)).thenReturn(Optional.of(balance(OLD)));

        service.balanceOf(ACCOUNT);

        verify(lastKnown, never()).put(any());
    }

    @Test
    void doesNotFallBackWhenCoreDoesNotKnowTheAccount() {
        when(core.fetchBalance(ACCOUNT)).thenThrow(new AccountNotFoundException(ACCOUNT));

        assertThatThrownBy(() -> service.balanceOf(ACCOUNT)).isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(lastKnown);
    }

    @Test
    void doesNotFallBackWhenCoreRejectsTheRequest() {
        when(core.fetchBalance(ACCOUNT))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        assertThatThrownBy(() -> service.balanceOf(ACCOUNT)).isInstanceOf(HttpClientErrorException.class);

        verifyNoInteractions(lastKnown);
    }

    private static AccountBalance balance(Instant asOf) {
        return new AccountBalance(ACCOUNT, new BigDecimal("152300.75"), "ARS", asOf);
    }

    private static CoreUnavailableException coreDown() {
        return new CoreUnavailableException("core down", null);
    }
}
