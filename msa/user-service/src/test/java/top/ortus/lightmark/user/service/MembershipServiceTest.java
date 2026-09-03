package top.ortus.lightmark.user.service;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.user.config.LightmarkMembershipProperties;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.user.UserLevelUpgradeInfoDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MembershipServiceTest {
    @Test
    void resolvesConfiguredLevelAndNextBenefit() {
        LightmarkMembershipProperties properties = new LightmarkMembershipProperties();
        LightmarkMembershipProperties.LevelRule level0 = rule((short) 0, 0, List.of());
        LightmarkMembershipProperties.LevelRule level1 = rule((short) 1, 100, List.of("discount"));
        properties.setLevels(List.of(level0, level1));
        MembershipService service = new MembershipService(properties);

        assertEquals(1, service.resolveLevelByPoints(120));
        UserDTO user = new UserDTO();
        user.setLevel((short) 0);
        user.setPoints(20);
        UserLevelUpgradeInfoDTO info = service.getUpgradeInfo(user);
        assertEquals(80, info.getPointsNeeded());
        assertEquals(List.of("discount"), info.getBenefits());
    }

    @Test
    void returnsNoNextLevelWhenAtHighestLevel() {
        MembershipService service = new MembershipService(new LightmarkMembershipProperties());
        UserDTO user = new UserDTO();
        user.setLevel((short) 3);
        user.setPoints(2000);
        assertEquals(0, service.getUpgradeInfo(user).getPointsNeeded());
    }

    private LightmarkMembershipProperties.LevelRule rule(short level, int threshold, List<String> benefits) {
        LightmarkMembershipProperties.LevelRule rule = new LightmarkMembershipProperties.LevelRule();
        rule.setLevel(level);
        rule.setPointsThreshold(threshold);
        rule.setBenefits(benefits);
        return rule;
    }
}
