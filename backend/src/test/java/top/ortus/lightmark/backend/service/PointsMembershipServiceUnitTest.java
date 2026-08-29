package top.ortus.lightmark.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import top.ortus.lightmark.backend.dao.User;
import top.ortus.lightmark.backend.dao.UserRepositoryImpl;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PointsMembershipServiceUnitTest {

    private UserRepositoryImpl userRepository;
    private MembershipService membershipService;
    private PointsMembershipService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryImpl.class);
        membershipService = mock(MembershipService.class);
        service = new PointsMembershipService(userRepository, membershipService);
    }

    @ParameterizedTest
    @CsvSource({
        "0,0",
        "-10,0",
        "49.99,0",
        "50,1",
        "680,13",
        "699.99,13"
    })
    void calculateRewardPointsShouldRoundDown(String paidAmount, int expectedPoints) {
        assertThat(service.calculateRewardPoints(new BigDecimal(paidAmount))).isEqualTo(expectedPoints);
    }

    @Test
    void calculateRewardPointsShouldReturnZeroForNullAmount() {
        assertThat(service.calculateRewardPoints(null)).isZero();
    }

    @Test
    void awardPointsShouldUpdateUserLevelAndWriteLog() {
        User user = userWithPoints(490);
        when(userRepository.findById("2")).thenReturn(user);
        when(membershipService.resolveLevelByPoints(503)).thenReturn((short) 2);

        service.awardPoints("2", "9001", "FLIGHT_PAY", new BigDecimal("680"));

        assertThat(user.getPoints()).isEqualTo(503);
        assertThat(user.getLevel()).isEqualTo((short) 2);
        verify(userRepository).update(user);
        verify(userRepository).insertPointsLog("2", 1, 13, "FLIGHT_PAY", "9001");
    }

    @Test
    void revokePointsShouldNotDropBalanceBelowZero() {
        User user = userWithPoints(5);
        when(userRepository.findById("2")).thenReturn(user);
        when(membershipService.resolveLevelByPoints(0)).thenReturn((short) 0);

        service.revokePoints("2", "9001", "FLIGHT_REFUND", new BigDecimal("680"));

        assertThat(user.getPoints()).isZero();
        assertThat(user.getLevel()).isZero();
        verify(userRepository).update(user);
        verify(userRepository).insertPointsLog("2", 2, -13, "FLIGHT_REFUND", "9001");
    }

    @Test
    void changePointsShouldSkipRepositoryWhenNoRewardPoints() {
        service.awardPoints("2", "9001", "FLIGHT_PAY", new BigDecimal("49.99"));

        verifyNoInteractions(userRepository, membershipService);
    }

    @Test
    void refreshLevelShouldResolveAndPersistCurrentLevel() {
        User user = userWithPoints(1000);
        when(userRepository.findById("2")).thenReturn(user);
        when(membershipService.resolveLevelByPoints(1000)).thenReturn((short) 3);

        service.refreshLevel("2");

        assertThat(user.getLevel()).isEqualTo((short) 3);
        verify(userRepository).update(user);
        verify(userRepository, never()).insertPointsLog(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private User userWithPoints(int points) {
        User user = new User();
        user.setId("2");
        user.setPoints(points);
        user.setLevel((short) 1);
        return user;
    }
}
