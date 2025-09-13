package com.lhstack.data

import com.alibaba.fastjson2.JSONArray
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI.Borders
import com.lhstack.tools.plugins.Helper
import com.lhstack.tools.plugins.IPlugin
import org.jdesktop.swingx.VerticalLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class ProjectItem(val path: String, var date: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProjectItem

        return path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    override fun toString(): String {
        return """
            $path     $date
        """.trimIndent()
    }


}

class PluginImpl : IPlugin {

    companion object {
        const val PREFIX = "JTools:OpenProject"
        val propertiesComponent: PropertiesComponent = PropertiesComponent.getInstance()
        val history =
            propertiesComponent.getValue("${PREFIX}:History")
        val historyList =
            history?.let { JSONArray.parseArray(it).toJavaList(ProjectItem::class.java) }
                ?: mutableListOf<ProjectItem>()
        val listModel: CollectionComboBoxModel<ProjectItem> = CollectionComboBoxModel(historyList)
        val modelDataListenerCache = mutableMapOf<String, ListDataListener>()
        val componentCache = mutableMapOf<String, JComponent>()
        val jListRender = object : ListCellRenderer<ProjectItem> {
            override fun getListCellRendererComponent(
                list: JList<out ProjectItem?>,
                value: ProjectItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                if (value == null) {
                    return JLabel("空的")
                }
                val panel = JPanel(VerticalLayout())
                panel.border = Borders.empty(5, 0)
                panel.add(JLabel(value.path, JLabel.LEFT).apply {
                    this.border = Borders.empty(0, 0, 5, 0)
                })
                panel.add(JLabel(value.date, JLabel.LEFT))
                if (isSelected) {
                    panel.setBackground(list.selectionBackground)
                    panel.setForeground(list.selectionForeground)
                } else {
                    panel.setBackground(list.getBackground())
                    panel.setForeground(list.getForeground())
                }
                panel.setFont(list.getFont())
                return panel
            }

        }

        val comboBoxRender = object : ListCellRenderer<ProjectItem> {
            override fun getListCellRendererComponent(
                list: JList<out ProjectItem?>,
                value: ProjectItem?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                if (value == null) {
                    return JLabel("")
                }
                val panel = JPanel(VerticalLayout())
                panel.add(JLabel(value.path, JLabel.LEFT).apply {
                    this.border = Borders.empty(0, 0, 2, 0)
                })
                panel.add(JLabel(value.date, JLabel.LEFT))
                if (isSelected) {
                    panel.setBackground(list.selectionBackground)
                    panel.setForeground(list.selectionForeground)
                } else {
                    panel.setBackground(list.getBackground())
                    panel.setForeground(list.getForeground())
                }
                panel.setFont(list.getFont())
                return panel
            }

        }
    }

    override fun pluginIcon(): Icon = Helper.findIcon("pane.svg", PluginImpl::class.java)

    override fun pluginTabIcon(): Icon = Helper.findIcon("tab.svg", PluginImpl::class.java)


    override fun closeProject(project: Project) {
        componentCache.remove(project.locationHash)
        modelDataListenerCache.remove(project.locationHash)?.let { listModel.removeListDataListener(it) }
    }

    override fun createPanel(project: Project): JComponent {
        return componentCache.computeIfAbsent(project.locationHash) {
            JPanel(BorderLayout()).apply {
                val comboBox = ComboBox<ProjectItem>(listModel)
                comboBox.preferredSize = Dimension(-1, 45)
                ComboboxSpeedSearch.installOn(comboBox)
                comboBox.renderer = comboBoxRender
                if (historyList.isNotEmpty()) {
                    comboBox.selectedItem = historyList[0]
                }
                val withBrowseButton = ComponentWithBrowseButton(comboBox) {
                    val path: String = if (comboBox.selectedItem == null) {
                        project.guessProjectDir()?.parent?.path ?: System.getProperty("user.dir")
                    } else {
                        (comboBox.selectedItem as ProjectItem).path
                    }
                    val f = File(path)
                    if (!f.exists()) {
                        val result = Messages.showYesNoDialog(
                            "文件夹不存在,是否移除此选项",
                            "提示",
                            "确认",
                            "取消",
                            AllIcons.General.ErrorDialog
                        )
                        if (result == Messages.YES) {
                            listModel.remove(comboBox.selectedItem as ProjectItem)
                            propertiesComponent.setValue("${PREFIX}:History", JSONArray.toJSONString(historyList))
                            if (historyList.isNotEmpty()) {
                                comboBox.selectedItem = historyList[0]
                            }
                        }
                        return@ComponentWithBrowseButton
                    }

                    val file = VirtualFileManager.getInstance().findFileByUrl("file://${path}")
                    val chooserDescriptor = FileChooserDescriptor(false, true, false, false, false, false)
                    chooserDescriptor.isForcedToUseIdeaFileChooser = true
                    FileChooser.chooseFile(chooserDescriptor, project, file)?.apply {

                        if (this.path == project.guessProjectDir()?.path) {
                            Messages.showInfoMessage("你已经打开了当前项目了,请不要重复打开", "重复打开提示")
                            return@ComponentWithBrowseButton
                        }
                        for (p in ProjectManager.getInstance().openProjects) {
                            if (this.path == p.guessProjectDir()?.path) {
                                ProjectUtil.focusProjectWindow(p, true)
                                return@ComponentWithBrowseButton
                            }
                        }

                        ProjectManager.getInstance().loadAndOpenProject(this.path)
                        if (historyList.size >= 100) {
                            listModel.remove(historyList.size - 1)
                        }
                        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        val item = ProjectItem(this.path, time)
                        if (!historyList.contains(item)) {
                            listModel.add(0,item)
                            comboBox.selectedItem = item
                        } else {
                            listModel.remove(item)
                            listModel.add(0,item)
                            comboBox.selectedItem = item
                        }
                        propertiesComponent.setValue("${PREFIX}:History", JSONArray.toJSONString(historyList))
                    }
                }
                this.add(withBrowseButton, BorderLayout.NORTH)
                this.add(JPanel(BorderLayout()).apply {
                    this.add(JLabel("历史记录: ", JLabel.LEFT).apply {
                        this.border = Borders.empty(10, 5, 2, 0)
                    }, BorderLayout.NORTH)
                    this.add(JBScrollPane(JBList<ProjectItem>(listModel).apply {
                        val that = this
                        this.border = Borders.empty(0, 10)
                        this.cellRenderer = jListRender
                        val popupFactory = JBPopupFactory.getInstance()

                        this.addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                val point = e.point
                                val index = that.locationToIndex(point)
                                if (index >= 0) {
                                    val cellBounds = that.getCellBounds(index, index)
                                    if (cellBounds != null && cellBounds.contains(point)) {
                                        if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
                                            if (that.selectedValue != null) {
                                                val f = File(that.selectedValue.path)
                                                if (!f.exists()) {
                                                    val result = Messages.showYesNoDialog(
                                                        "文件夹不存在,是否移除此选项",
                                                        "提示",
                                                        "确认",
                                                        "取消",
                                                        AllIcons.General.ErrorDialog
                                                    )
                                                    if (result == Messages.YES) {
                                                        listModel.remove(comboBox.selectedItem as ProjectItem)
                                                        propertiesComponent.setValue(
                                                            "${PREFIX}:History",
                                                            JSONArray.toJSONString(historyList)
                                                        )
                                                        if (historyList.isNotEmpty()) {
                                                            comboBox.selectedItem = historyList[0]
                                                        }
                                                    }
                                                    return
                                                }
                                                if (that.selectedValue.path == project.guessProjectDir()?.path) {
                                                    Messages.showInfoMessage(
                                                        "你已经打开了当前项目了,请不要重复打开",
                                                        "重复打开提示"
                                                    )
                                                    return
                                                }
                                                for (p in ProjectManager.getInstance().openProjects) {
                                                    if (that.selectedValue.path == p.guessProjectDir()?.path) {
                                                        ProjectUtil.focusProjectWindow(p, true)
                                                        return
                                                    }
                                                }
                                                val item = ProjectItem(
                                                    that.selectedValue.path,
                                                    LocalDateTime.now()
                                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                )
                                                listModel.remove(item)
                                                listModel.add(0,item)
                                                comboBox.selectedItem = item
                                                that.setSelectedValue(item, true)
                                                propertiesComponent.setValue(
                                                    "${PREFIX}:History",
                                                    JSONArray.toJSONString(historyList)
                                                )
                                                ProjectManager.getInstance().loadAndOpenProject(item.path)
                                            }

                                        }
                                        if (SwingUtilities.isRightMouseButton(e)) {
                                            that.selectedIndex = index
                                            val popup = popupFactory.createActionGroupPopup(
                                                "操作",
                                                DefaultActionGroup().apply {
                                                    this.add(object: AnAction({"删除"}, AllIcons.Actions.RemoveMulticaret){
                                                        override fun actionPerformed(e: AnActionEvent) {
                                                            if(that.selectedValue != null){
                                                                listModel.remove(that.selectedValue)
                                                                if(historyList.isNotEmpty()){
                                                                    comboBox.selectedItem = historyList[0]
                                                                }else {
                                                                    comboBox.selectedItem = null
                                                                }
                                                                propertiesComponent.setValue(
                                                                    "${PREFIX}:History",
                                                                    JSONArray.toJSONString(historyList)
                                                                )
                                                            }
                                                        }
                                                    })
                                                    this.add(object: AnAction({"打开"}, AllIcons.Actions.AddMulticaret){
                                                        override fun actionPerformed(e: AnActionEvent) {
                                                            if (that.selectedValue != null) {
                                                                val f = File(that.selectedValue.path)
                                                                if (!f.exists()) {
                                                                    val result = Messages.showYesNoDialog(
                                                                        "文件夹不存在,是否移除此选项",
                                                                        "提示",
                                                                        "确认",
                                                                        "取消",
                                                                        AllIcons.General.ErrorDialog
                                                                    )
                                                                    if (result == Messages.YES) {
                                                                        listModel.remove(comboBox.selectedItem as ProjectItem)
                                                                        propertiesComponent.setValue(
                                                                            "${PREFIX}:History",
                                                                            JSONArray.toJSONString(historyList)
                                                                        )
                                                                        if (historyList.isNotEmpty()) {
                                                                            comboBox.selectedItem = historyList[0]
                                                                        }
                                                                    }
                                                                    return
                                                                }
                                                                if (that.selectedValue.path == project.guessProjectDir()?.path) {
                                                                    Messages.showInfoMessage(
                                                                        "你已经打开了当前项目了,请不要重复打开",
                                                                        "重复打开提示"
                                                                    )
                                                                    return
                                                                }
                                                                for (p in ProjectManager.getInstance().openProjects) {
                                                                    if (that.selectedValue.path == p.guessProjectDir()?.path) {
                                                                        ProjectUtil.focusProjectWindow(p, true)
                                                                        return
                                                                    }
                                                                }
                                                                val item = ProjectItem(
                                                                    that.selectedValue.path,
                                                                    LocalDateTime.now()
                                                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                                                )
                                                                listModel.remove(item)
                                                                listModel.add(0,item)
                                                                comboBox.selectedItem = item
                                                                that.setSelectedValue(item, true)
                                                                propertiesComponent.setValue(
                                                                    "${PREFIX}:History",
                                                                    JSONArray.toJSONString(historyList)
                                                                )
                                                                ProjectManager.getInstance().loadAndOpenProject(item.path)
                                                            }
                                                        }
                                                    })
                                                },
                                                DataContext.EMPTY_CONTEXT,
                                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                true
                                            )
                                            popup.show(RelativePoint(e.component,Point(e.x + 10,e.y + 10)))
                                        }
                                    } else {
                                        that.clearSelection()
                                        that.isFocusable = false
                                    }
                                } else {
                                    that.clearSelection()
                                    that.isFocusable = false
                                }

                            }
                        })
                    }), BorderLayout.CENTER)
                    this.add(JLabel("总数: ${historyList.size}", JLabel.LEFT).apply {
                        val that = this
                        val listener = object: ListDataListener{
                            override fun intervalAdded(e: ListDataEvent) {
                                that.text = "总数: ${historyList.size}"
                            }
                            override fun intervalRemoved(e: ListDataEvent) {
                                that.text = "总数: ${historyList.size}"
                            }
                            override fun contentsChanged(e: ListDataEvent) {
                                that.text = "总数: ${historyList.size}"
                            }
                        }
                        listModel.addListDataListener(listener)
                        modelDataListenerCache[project.locationHash] = listener
                        this.border = Borders.empty(5, 5, 10, 0)
                        this.font = Font("宋体", Font.BOLD, 16)
                    }, BorderLayout.SOUTH)
                }, BorderLayout.CENTER)
            }
        }
    }

    override fun pluginName(): String = "项目管理"

    override fun pluginDesc(): String = "解决idea2025版本以上打开项目没有记忆的问题"

    override fun pluginVersion(): String = "0.0.1"
}