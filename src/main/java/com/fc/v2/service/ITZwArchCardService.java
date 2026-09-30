package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwArchCard;

import java.util.List;

/**
 * 水源地/水厂档案卡 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITZwArchCardService {

    /** 按主键查询 */
    TZwArchCard selectTZwArchCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TZwArchCard> selectTZwArchCardList(Wrapper<TZwArchCard> queryWrapper);

    /** 新增 */
    int insertTZwArchCard(TZwArchCard record);

    /** 修改 */
    int updateTZwArchCard(TZwArchCard record);

    /** 批量删除 */
    int deleteTZwArchCardByIds(String ids);

    /** 按主键删除 */
    int deleteTZwArchCardById(Long id);
}
