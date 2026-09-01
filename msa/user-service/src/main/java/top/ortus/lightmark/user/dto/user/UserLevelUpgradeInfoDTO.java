package top.ortus.lightmark.user.dto.user;

import java.util.List;

public class UserLevelUpgradeInfoDTO {
    private short level;
    private int pointsNeeded;
    private List<String> benefits;

    public UserLevelUpgradeInfoDTO() {
    }

    public UserLevelUpgradeInfoDTO(short level, int pointsNeeded, List<String> benefits) {
        this.level = level;
        this.pointsNeeded = pointsNeeded;
        this.benefits = benefits;
    }

    public short getLevel() {
        return level;
    }

    public void setLevel(short level) {
        this.level = level;
    }

    public int getPointsNeeded() {
        return pointsNeeded;
    }

    public void setPointsNeeded(int pointsNeeded) {
        this.pointsNeeded = pointsNeeded;
    }

    public List<String> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<String> benefits) {
        this.benefits = benefits;
    }
}
