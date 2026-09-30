package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwSamTask;
import com.fc.v2.service.ITZwSamTaskService;
import com.fc.v2.service.zw.SamRunResult;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 采样任务到期派单 Controller（scheduling-job 形状：翻台账 + 手动跑一轮 + 撤单/转理）。
 *
 * @author fuce
 * @date 2026-09-29
 */
@Api(value = "采样任务到期派单")
@Controller
@RequestMapping("/zwSamTask")
public class ZwSamTaskController extends BaseController {

    private final String prefix = "admin/zwSamTask";

    @Autowired
    private ITZwSamTaskService zwSamTaskService;

    @ApiOperation(value = "采样台账跳转", notes = "采样台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwSamTask:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "采样任务台账查询", action = "list")
    @ApiOperation(value = "台账查询", notes = "挂起/撤单/转理条目照常可翻到")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwSamTask:list")
    @ResponseBody
    public ResultTable list(TZwSamTask record) {
        QueryWrapper<TZwSamTask> queryWrapper = new QueryWrapper<TZwSamTask>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getItemNo()), "item_no", record.getItemNo());
        queryWrapper.like(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        queryWrapper.orderByDesc("due_at");
        startPage();
        PageInfo<TZwSamTask> page =
                new PageInfo<TZwSamTask>(zwSamTaskService.selectTZwSamTaskList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "采样派单跑一轮", action = "run")
    @ApiOperation(value = "跑一轮", notes = "窗口外静默/窗口内无活/执行出错 三种情形在返回里分得清")
    @PostMapping("/run")
    @RequiresPermissions("zw:zwSamTask:run")
    @ResponseBody
    public AjaxResult run() {
        SamRunResult result = zwSamTaskService.runOnce(new Date());
        return AjaxResult.successData(200, result);
    }

    @Log(title = "采样任务撤单", action = "cancel")
    @ApiOperation(value = "撤单", notes = "仅待派/挂起可撤；留痕，不覆盖采样日与派出时刻")
    @PostMapping("/cancel")
    @RequiresPermissions("zw:zwSamTask:cancel")
    @ResponseBody
    public AjaxResult cancel(Long id, String reason) {
        return toAjax(zwSamTaskService.cancel(id, reason, new Date()) ? 1 : 0);
    }

    @Log(title = "采样任务转作他理", action = "transfer")
    @ApiOperation(value = "转作他理", notes = "仅待派/挂起可转；留痕，不覆盖采样日与派出时刻")
    @PostMapping("/transfer")
    @RequiresPermissions("zw:zwSamTask:transfer")
    @ResponseBody
    public AjaxResult transfer(Long id, String reason) {
        return toAjax(zwSamTaskService.transfer(id, reason, new Date()) ? 1 : 0);
    }
}
