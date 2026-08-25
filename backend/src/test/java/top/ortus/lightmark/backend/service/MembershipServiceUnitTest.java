package top.ortus.lightmark.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import top.ortus.lightmark.backend.config.lightmarkMembershipProperties;
import top.ortus.lightmark.backend.dto.UserDTO;
import top.ortus.lightmark.backend.dto.user.UserLevelUpgradeInfoDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipServiceUnitTest {

    @ParameterizedTest
    @CsvSource({
        "0,0",
        "99,0",
        "100,1",
        "499,1",
        "500,2",
        "999,2",
        "1000,3"
    })
    void resolveLevelByPointsShouldUseDefaultThresholds(int points, short expectedLevel) {
        MembershipService service = new MembershipService(new lightmarkMembershipProperties());

        assertThat(service.resolveLevelByPoints(points)).isEqualTo(expectedLevel);
    }

    @Test
    void getUpgradeInfoShouldReturnNextLevelGapAndBenefits() {
        MembershipService service = new MembershipService(new lightmarkMembershipProperties());
        UserDTO user = new UserDTO();
        user.setLevel((short) 1);
        user.setPoints(450);

        UserLevelUpgradeInfoDTO info = service.getUpgradeInfo(user);

        assertThat(info.getLevel()).isEqualTo((short) 1);
        assertThat(info.getPointsNeeded()).isEqualTo(50);
        assertThat(info.getBenefits()).contains("更高折扣", "专属客服");
    }

    @Test
    void getUpgradeInfoShouldReturnZeroWhenAlreadyAtMaxLevel() {
        MembershipService service = new MembershipService(new lightmarkMembershipProperties());
        UserDTO user = new UserDTO();
        user.setLevel((short) 3);
        user.setPoints(1200);

        UserLevelUpgradeInfoDTO info = service.getUpgradeInfo(user);

        assertThat(info.getPointsNeeded()).isZero();
        assertThat(info.getBenefits()).isEmpty();
    }

    @Test
    void customLevelConfigShouldOverrideDefaults() {
        lightmarkMembershipProperties properties = new lightmarkMembershipProperties();
        properties.setLevels(List.of(
            rule((short) 0, 0, "基础"),
            rule((short) 5, 200, "白金"),
            rule((short) 8, 800, "黑钻")
        ));
        MembershipService service = new MembershipService(properties);

        assertThat(service.resolveLevelByPoints(199)).isZero();
        assertThat(service.resolveLevelByPoints(200)).isEqualTo((short) 5);
        assertThat(service.resolveLevelByPoints(900)).isEqualTo((short) 8);
    }

    private lightmarkMembershipProperties.LevelRule rule(short level, int threshold, String benefit) {
        lightmarkMembershipProperties.LevelRule rule = new lightmarkMembershipProperties.LevelRule();
        rule.setLevel(level);
        rule.setPointsThreshold(threshold);
        rule.setBenefits(List.of(benefit));
        return rule;
    }
}
