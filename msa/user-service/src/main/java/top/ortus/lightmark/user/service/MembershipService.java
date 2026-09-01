package top.ortus.lightmark.user.service;

import org.springframework.stereotype.Service;
import top.ortus.lightmark.user.config.LightmarkMembershipProperties;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.user.UserLevelUpgradeInfoDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MembershipService {

    private final LightmarkMembershipProperties membershipProperties;

    public MembershipService(LightmarkMembershipProperties membershipProperties) {
        this.membershipProperties = membershipProperties;
    }

    public UserLevelUpgradeInfoDTO getUpgradeInfo(UserDTO user) {
        List<LightmarkMembershipProperties.LevelRule> rules = effectiveRules();
        short currentLevel = user == null ? 0 : user.getLevel();
        int currentPoints = user == null ? 0 : user.getPoints();

        LightmarkMembershipProperties.LevelRule nextRule = rules.stream()
                .filter(r -> r.getLevel() > currentLevel)
                .min(Comparator.comparingInt(LightmarkMembershipProperties.LevelRule::getLevel))
                .orElse(null);

        if (nextRule == null) {
            return new UserLevelUpgradeInfoDTO(currentLevel, 0, List.of());
        }

        int pointsNeeded = nextRule.getPointsThreshold() - currentPoints;
        if (pointsNeeded < 0) {
            pointsNeeded = 0;
        }
        return new UserLevelUpgradeInfoDTO(currentLevel, pointsNeeded, nextRule.getBenefits());
    }

    public short resolveLevelByPoints(int points) {
        short level = 0;
        for (LightmarkMembershipProperties.LevelRule rule : effectiveRules()) {
            if (points >= rule.getPointsThreshold()) {
                level = rule.getLevel();
            }
        }
        return level;
    }

    private List<LightmarkMembershipProperties.LevelRule> effectiveRules() {
        List<LightmarkMembershipProperties.LevelRule> rules = membershipProperties.getLevels();
        if (rules != null && !rules.isEmpty()) {
            return rules;
        }
        List<LightmarkMembershipProperties.LevelRule> defaults = new ArrayList<>();
        defaults.add(rule((short) 0, 0, List.of()));
        defaults.add(rule((short) 1, 100, List.of("VIP 折扣", "积分加成")));
        defaults.add(rule((short) 2, 500, List.of("更高折扣", "专属客服")));
        defaults.add(rule((short) 3, 1000, List.of("最高折扣", "专属客服", "生日礼包")));
        return defaults;
    }

    private LightmarkMembershipProperties.LevelRule rule(short level, int pointsThreshold, List<String> benefits) {
        LightmarkMembershipProperties.LevelRule rule = new LightmarkMembershipProperties.LevelRule();
        rule.setLevel(level);
        rule.setPointsThreshold(pointsThreshold);
        rule.setBenefits(benefits);
        return rule;
    }
}

