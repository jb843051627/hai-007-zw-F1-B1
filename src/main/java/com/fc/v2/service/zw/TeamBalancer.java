package com.fc.v2.service.zw;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fc.v2.model.auto.TSysDepartment;

/**
 * 班组负载加权分派。
 *
 * <p>负载 = 班组名下当前已派（未结）事项条数；负载越高权重越低，接得越少的班组越优先。
 * 权重取 {@code 1/(load+1)}；同权重（同负载）按班组主键升序，保证分派确定可复现。
 * 每派出一条，本轮内即时加一记负载，后续条目按新负载再平衡。
 *
 * @author fuce
 * @date 2026-09-29
 */
public class TeamBalancer {

    private final Map<Long, Integer> load = new HashMap<Long, Integer>();

    public TeamBalancer(Map<Long, Integer> currentLoad) {
        if (currentLoad != null) {
            this.load.putAll(currentLoad);
        }
    }

    /** 在当前在岗班组里选一个：负载最低者优先，并列取主键最小者；无班组返回 null */
    public TSysDepartment pick(List<TSysDepartment> activeTeams) {
        TSysDepartment chosen = null;
        double bestWeight = -1d;
        for (TSysDepartment dept : activeTeams) {
            double w = 1d / (loadOf(dept.getId()) + 1d);
            if (w > bestWeight || (w == bestWeight && chosen != null
                    && dept.getId() < chosen.getId())) {
                bestWeight = w;
                chosen = dept;
            }
        }
        return chosen;
    }

    /** 派出后记一笔负载 */
    public void assigned(Long teamId) {
        if (teamId != null) {
            load.put(teamId, loadOf(teamId) + 1);
        }
    }

    public int loadOf(Long teamId) {
        Integer n = load.get(teamId);
        return n == null ? 0 : n;
    }
}
