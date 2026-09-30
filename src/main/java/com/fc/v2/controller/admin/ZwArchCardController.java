package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwArchCard;
import com.fc.v2.service.ITZwArchCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 水源地/水厂档案卡 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "水源地/水厂档案卡")
@Controller
@RequestMapping("/ZwArchCardController")
public class ZwArchCardController extends BaseController {

    private final String prefix = "admin/zwArchCard";

    @Autowired
    private ITZwArchCardService zwArchCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwArchCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "水源地/水厂档案卡集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwArchCard:list")
    @ResponseBody
    public ResultTable list(TZwArchCard record) {
        QueryWrapper<TZwArchCard> queryWrapper = new QueryWrapper<TZwArchCard>();
        startPage();
        com.github.pagehelper.PageInfo<TZwArchCard> page =
                new com.github.pagehelper.PageInfo<TZwArchCard>(zwArchCardService.selectTZwArchCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "水源地/水厂档案卡新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("zw:zwArchCard:add")
    @ResponseBody
    public AjaxResult add(TZwArchCard record) {
        return toAjax(zwArchCardService.insertTZwArchCard(record));
    }

    @Log(title = "水源地/水厂档案卡修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("zw:zwArchCard:edit")
    @ResponseBody
    public AjaxResult editSave(TZwArchCard record) {
        return toAjax(zwArchCardService.updateTZwArchCard(record));
    }

    @Log(title = "水源地/水厂档案卡删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("zw:zwArchCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(zwArchCardService.deleteTZwArchCardByIds(ids));
    }
}
