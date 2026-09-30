package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwCaseFlow;
import com.fc.v2.service.ITZwCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 水质异常处置工单 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "水质异常处置工单")
@Controller
@RequestMapping("/zwCaseFlow")
public class ZwCaseFlowController extends BaseController {

    private final String prefix = "admin/zwCaseFlow";

    @Autowired
    private ITZwCaseFlowService zwCaseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zwCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "水质异常处置工单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("zwCaseFlow:list")
    @ResponseBody
    public ResultTable list(TZwCaseFlow record) {
        QueryWrapper<TZwCaseFlow> queryWrapper = new QueryWrapper<TZwCaseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TZwCaseFlow> page =
                new com.github.pagehelper.PageInfo<TZwCaseFlow>(zwCaseFlowService.selectTZwCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "水质异常处置工单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("zwCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(zwCaseFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "水质异常处置工单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("zwCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(zwCaseFlowService.rollback(id, remark) != null ? 1 : 0);
    }
}
