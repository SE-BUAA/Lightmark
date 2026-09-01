package top.ortus.lightmark.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "lightmark.membership")
public class LightmarkMembershipProperties {

    private List<LevelRule> levels = new ArrayList<>();

    public List<LevelRule> getLevels() {
        return levels;
    }

    public void setLevels(List<LevelRule> levels) {
        this.levels = levels;
    }

    public static class LevelRule {
        private short level;
        private int pointsThreshold;
        private List<String> benefits = new ArrayList<>();

        public short getLevel() {
            return level;
        }

        public void setLevel(short level) {
            this.level = level;
        }

        public int getPointsThreshold() {
            return pointsThreshold;
        }

        public void setPointsThreshold(int pointsThreshold) {
            this.pointsThreshold = pointsThreshold;
        }

        public List<String> getBenefits() {
            return benefits;
        }

        public void setBenefits(List<String> benefits) {
            this.benefits = benefits;
        }
    }
}

