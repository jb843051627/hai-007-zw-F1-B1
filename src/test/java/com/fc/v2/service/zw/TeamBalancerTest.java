package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TSysDepartment;

/**
 * 负载加权：负载越高权重越低；同负载按主键升序，保证确定可复现。
 */
public class TeamBalancerTest {

    private static TSysDepartment dept(long id) {
        TSysDepartment d = new TSysDepartment();
        d.setId(id);
        d.setDeptName("班组" + id);
        return d;
    }

    @Test
    public void picksLeastLoadedTeamAndRebalancesWithinRound() {
        Map<Long, Integer> load = new HashMap<Long, Integer>();
        load.put(1L, 5); // 负载最高，权重最低
        load.put(2L, 0); // 接得最少，优先
        load.put(3L, 2);
        TeamBalancer balancer = new TeamBalancer(load);
        List<TSysDepartment> teams = new ArrayList<TSysDepartment>(
                Arrays.asList(dept(1L), dept(2L), dept(3L)));

        assertEquals(2L, balancer.pick(teams).getId());
        balancer.assigned(2L);
        // 2 班记一笔后负载 1，仍最轻
        assertEquals(2L, balancer.pick(teams).getId());
        balancer.assigned(2L);
        // 2 班记第二笔后负载 2，与 3 班持平，权重相同取小主键 → 仍是 2 班
        assertEquals(2L, balancer.pick(teams).getId());
        balancer.assigned(2L);
        // 2 班第三笔后负载 3，超过 3 班的 2 → 轮给 3 班
        assertEquals(3L, balancer.pick(teams).getId());
    }

    @Test
    public void tieBreaksBySmallestId() {
        TeamBalancer balancer = new TeamBalancer(new HashMap<Long, Integer>());
        assertEquals(1L, balancer.pick(Arrays.asList(dept(3L), dept(1L), dept(2L))).getId());
    }

    @Test
    public void noTeamReturnsNull() {
        assertNull(new TeamBalancer(null).pick(new ArrayList<TSysDepartment>()));
    }
}
