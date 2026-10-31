"use client";

import { useEffect, useState, type FormEvent } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import Link from "next/link";
import { ArrowDownAZ, ChevronDown, ChevronRight, FolderClosed, FolderPlus, LogOut, MessageSquare, MessageSquarePlus, MoreHorizontal, PanelLeftClose, PanelLeftOpen, Pencil, Plus, Search, Send, Settings2, Sparkles, Trash2, UserRound, X } from "lucide-react";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/src/components/_shared/ui/dropdown-menu";
import { logoutCurrentSession } from "@/src/lib/auth/logout";
import { resolveLogoutRedirectPath } from "@/src/lib/auth/logoutRedirect";
import { FEEDBACK_HREF } from "@/src/lib/routing/helpCenterRoutes";
import { LOGOUT_REDIRECT_STORAGE_KEY } from "@/src/stores/auth";
import type { WorkbenchProject, WorkbenchSession } from "./workbenchApi";
import styles from "./workbench.module.css";

type WorkbenchSidebarProps = {
  projects: WorkbenchProject[];
  sessions: WorkbenchSession[];
  activeProjectId: string | null;
  activeSessionId: string | null;
  accountName: string;
  collapsed: boolean;
  mobileOpen: boolean;
  busy: boolean;
  onToggleCollapsed: () => void;
  onCloseMobile: () => void;
  onNewConversation: () => void;
  onCreateWorkspace: (name: string) => Promise<void>;
  onRenameWorkspace: (projectId: string, name: string) => Promise<void>;
  onDeleteWorkspace: (projectId: string) => Promise<void>;
  onNewConversationInProject: (projectId: string) => void;
  onSelectProject: (projectId: string) => void;
  onSelectSession: (sessionId: string) => void;
};

export function WorkbenchSidebar({
  projects,
  sessions,
  activeProjectId,
  activeSessionId,
  accountName,
  collapsed,
  mobileOpen,
  busy,
  onToggleCollapsed,
  onCloseMobile,
  onNewConversation,
  onCreateWorkspace,
  onRenameWorkspace,
  onDeleteWorkspace,
  onNewConversationInProject,
  onSelectProject,
  onSelectSession,
}: WorkbenchSidebarProps) {
  const [searchOpen, setSearchOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [sortByName, setSortByName] = useState(false);
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);
  const [workspaceName, setWorkspaceName] = useState("");
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);
  const [workspaceSaving, setWorkspaceSaving] = useState(false);
  const [expandedProjectId, setExpandedProjectId] = useState(activeProjectId);
  const [openProjectMenuId, setOpenProjectMenuId] = useState<string | null>(null);
  const [projectAction, setProjectAction] = useState<{ kind: "rename" | "delete"; id: string; name: string } | null>(null);
  const [renameDraft, setRenameDraft] = useState("");
  const [projectActionBusy, setProjectActionBusy] = useState(false);
  const [projectActionError, setProjectActionError] = useState<string | null>(null);
  const wide = !collapsed || mobileOpen;
  const activeProject = projects.find((project) => String(project.id) === activeProjectId);

  useEffect(() => {
    setExpandedProjectId(activeProjectId);
  }, [activeProjectId]);
  const query = wide ? search.trim().toLocaleLowerCase() : "";
  const matchingSessions = query
    ? sessions.filter((session) => (session.title || session.lastUserMessage || "").toLocaleLowerCase().includes(query))
    : sessions;
  const visibleProjects = projects.filter((project) => !query
    || project.name.toLocaleLowerCase().includes(query)
    || (String(project.id) === activeProjectId && matchingSessions.length > 0));
  if (sortByName) visibleProjects.sort((a, b) => a.name.localeCompare(b.name));

  const submitWorkspace = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = workspaceName.trim();
    if (!name || workspaceSaving || busy) return;
    setWorkspaceSaving(true);
    setWorkspaceError(null);
    try {
      await onCreateWorkspace(name);
      setCreatingWorkspace(false);
      setWorkspaceName("");
      setSearch("");
    } catch (cause) {
      setWorkspaceError(cause instanceof Error ? cause.message : "创建工作区失败，请稍后重试");
    } finally {
      setWorkspaceSaving(false);
    }
  };

  const openProjectAction = (kind: "rename" | "delete", id: string, name: string) => {
    setOpenProjectMenuId(null);
    setProjectAction({ kind, id, name });
    setRenameDraft(name);
    setProjectActionError(null);
  };

  const submitRename = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (projectAction?.kind !== "rename" || projectActionBusy) return;
    const name = renameDraft.trim();
    if (!name) return;
    if (name === projectAction.name) { setProjectAction(null); return; }
    setProjectActionBusy(true);
    setProjectActionError(null);
    try {
      await onRenameWorkspace(projectAction.id, name);
      setProjectAction(null);
    } catch (cause) {
      setProjectActionError(cause instanceof Error ? cause.message : "重命名失败，请稍后重试");
    } finally {
      setProjectActionBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (projectAction?.kind !== "delete" || projectActionBusy) return;
    setProjectActionBusy(true);
    setProjectActionError(null);
    try {
      await onDeleteWorkspace(projectAction.id);
      setProjectAction(null);
    } catch (cause) {
      setProjectActionError(cause instanceof Error ? cause.message : "删除失败，请稍后重试");
    } finally {
      setProjectActionBusy(false);
    }
  };

  const signOut = () => {
    const redirectPath = resolveLogoutRedirectPath();
    try {
      sessionStorage.setItem(LOGOUT_REDIRECT_STORAGE_KEY, redirectPath);
    } catch {
      // Redirect still works when session storage is unavailable.
    }
    void logoutCurrentSession().finally(() => window.location.replace(redirectPath));
  };

  return (
    <aside className={`${styles.sidebar} ${!wide ? styles.sidebarCollapsed : ""} ${mobileOpen ? styles.sidebarOpen : ""}`} aria-label="工作台导航">
      <div className={styles.brandRow}>
        {wide ? (
          <>
            <span className={styles.brandMark} aria-hidden="true"><Sparkles size={17} strokeWidth={2.1} /></span>
            <span className={styles.brandName}>Deckwise Demo</span>
            <button className={styles.collapseButton} type="button" aria-label="收起侧栏" title="收起侧栏" onClick={onToggleCollapsed}>
              <PanelLeftClose size={18} strokeWidth={1.8} />
            </button>
          </>
        ) : (
          <button className={styles.railButton} type="button" aria-label="展开侧栏" title="展开侧栏" onClick={onToggleCollapsed}>
            <PanelLeftOpen size={19} strokeWidth={1.8} />
          </button>
        )}
        <button className={styles.mobileClose} type="button" aria-label="关闭导航" onClick={onCloseMobile}><X size={18} /></button>
      </div>

      <button className={styles.newButton} type="button" onClick={onNewConversation} disabled={busy}
        title={!wide ? "新建对话" : undefined} aria-label="新建对话">
        <Plus size={18} strokeWidth={1.9} />
        {wide && <span>新建对话</span>}
      </button>

      <div className={styles.sidebarBrowser}>
        {wide && <div className={styles.sectionHeader}>
          {searchOpen ? (
            <input autoFocus className={styles.workspaceSearch} value={search}
              onChange={(event) => setSearch(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Escape") { setSearch(""); setSearchOpen(false); } }}
              aria-label="搜索工作区和当前对话" placeholder="搜索工作区" />
          ) : <span className={styles.sectionTitle}>工作区</span>}
          <div className={styles.sectionActions}>
            <button type="button" className={styles.sectionAction} aria-label={searchOpen ? "关闭搜索" : "搜索工作区"}
              title={searchOpen ? "关闭搜索" : "搜索工作区"}
              onClick={() => { setSearchOpen(!searchOpen); setSearch(""); }}>
              {searchOpen ? <X size={16} /> : <Search size={16} />}
            </button>
            <button type="button" className={styles.sectionAction} aria-label={sortByName ? "按最近更新排序" : "按名称排序"}
              title={sortByName ? "按最近更新排序" : "按名称排序"} aria-pressed={sortByName}
              onClick={() => setSortByName((value) => !value)}><ArrowDownAZ size={16} /></button>
            <button type="button" className={styles.sectionAction} aria-label="新建工作区" title="新建工作区"
              disabled={busy || workspaceSaving} onClick={() => { setCreatingWorkspace(true); setWorkspaceError(null); }}>
              <FolderPlus size={17} />
            </button>
          </div>
        </div>}
        {wide && creatingWorkspace && (
          <form className={styles.workspaceForm} onSubmit={(event) => void submitWorkspace(event)}>
            <input autoFocus value={workspaceName} maxLength={80} aria-label="新工作区名称" placeholder="工作区名称"
              disabled={workspaceSaving} onChange={(event) => setWorkspaceName(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Escape") { setCreatingWorkspace(false); setWorkspaceName(""); } }} />
            <button type="submit" aria-label="创建工作区" disabled={!workspaceName.trim() || workspaceSaving}><Plus size={16} /></button>
            <button type="button" aria-label="取消创建工作区" onClick={() => { setCreatingWorkspace(false); setWorkspaceName(""); setWorkspaceError(null); }}><X size={15} /></button>
          </form>
        )}
        {wide && workspaceError && <p className={styles.sidebarError} role="alert">{workspaceError}</p>}
        {projects.length === 0 && wide && <p className={styles.sidebarHint}>创建工作区，或发送第一条消息</p>}
        {query && visibleProjects.length === 0 && <p className={styles.sidebarHint}>没有匹配的工作区或对话</p>}
        {visibleProjects.map((project) => {
          const id = String(project.id);
          const active = activeProjectId === id;
          const expanded = active && expandedProjectId === id;
          const menuOpen = openProjectMenuId === id;
          const visibleSessions = query && !project.name.toLocaleLowerCase().includes(query) ? matchingSessions : sessions;
          return (
            <div key={String(project.id)} className={styles.projectGroup}>
              <div className={`${styles.projectRowShell} ${active ? styles.projectRowActive : ""} ${menuOpen ? styles.projectMenuOpen : ""}`}>
                <button type="button" className={`${styles.navItem} ${styles.projectRow} ${active ? styles.navItemActive : ""}`}
                  onClick={() => {
                    if (active) setExpandedProjectId(expanded ? null : id);
                    else onSelectProject(id);
                  }} disabled={busy}
                  aria-label={project.name} aria-expanded={wide && expanded} title={!wide ? project.name : undefined}>
                  <span className={styles.projectLeadingIcon}>
                    <FolderClosed className={styles.projectFolderIcon} size={17} strokeWidth={1.8} />
                    <ChevronRight className={`${styles.projectExpandIcon} ${expanded ? styles.projectExpandIconOpen : ""}`} size={15} strokeWidth={2} />
                  </span>
                  {wide && <span className={styles.projectLabel}>{project.name}</span>}
                </button>
                {wide && <div className={styles.projectRowActions}>
                  <DropdownMenu open={menuOpen} onOpenChange={(open) => setOpenProjectMenuId(open ? id : null)}>
                    <DropdownMenuTrigger asChild>
                      <button type="button" className={styles.projectActionButton} disabled={busy}
                        aria-label={`工作区“${project.name}”的操作`} title="工作区操作">
                        <MoreHorizontal size={17} strokeWidth={2} />
                      </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent side="right" align="start" sideOffset={5} className={styles.projectMenu}>
                      <DropdownMenuItem onSelect={() => openProjectAction("rename", id, project.name)}>
                        <Pencil size={16} />重命名
                      </DropdownMenuItem>
                      <DropdownMenuItem onSelect={() => openProjectAction("delete", id, project.name)} className={styles.deleteMenuItem}>
                        <Trash2 size={16} />删除工作区
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                  <button type="button" className={styles.projectActionButton} disabled={busy}
                    aria-label={`在${project.name}中创建对话`} title="在此工作区新建对话"
                    onClick={() => onNewConversationInProject(id)}>
                    <MessageSquarePlus size={17} strokeWidth={1.8} />
                  </button>
                </div>}
              </div>
              {wide && expanded && (
                <div className={styles.projectSessions} aria-label={`${project.name}的对话`}>
                  {visibleSessions.length === 0 && <p className={styles.sidebarHint}>{query ? "没有匹配的对话" : "暂无对话"}</p>}
                  {visibleSessions.map((session) => (
                    <button key={String(session.id)} type="button"
                      className={`${styles.navItem} ${styles.sessionRow} ${activeSessionId === String(session.id) ? styles.sessionRowActive : ""}`}
                      onClick={() => onSelectSession(String(session.id))} disabled={busy}
                      aria-current={activeSessionId === String(session.id) ? "page" : undefined}>
                      <MessageSquare size={15} strokeWidth={1.8} />
                      <span>{session.title?.trim() || session.lastUserMessage?.trim() || "未命名对话"}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
        {!wide && activeProject && sessions.length > 0 && (
          <button className={styles.railButton} type="button" onClick={onToggleCollapsed}
            aria-label={`查看${activeProject.name}的对话`} title="查看对话">
            <MessageSquare size={18} strokeWidth={1.8} />
          </button>
        )}
      </div>

      <div className={styles.sidebarFooter}>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className={styles.accountButton} type="button" title={!wide ? accountName : undefined}
              aria-label="账户菜单">
              <span className={styles.avatar}><UserRound size={16} strokeWidth={1.8} /></span>
              {wide && <><span className={styles.accountName}>{accountName}</span><ChevronDown className={styles.accountChevron} size={14} /></>}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent side="top" align="start" sideOffset={8} className={styles.accountMenu}>
            <DropdownMenuItem asChild>
              <Link href="/app/settings/account" onClick={onCloseMobile}><Settings2 size={16} />设置</Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <a href={FEEDBACK_HREF} target="_blank" rel="noopener noreferrer"><Send size={16} />意见反馈</a>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onSelect={signOut} disabled={busy} className={styles.signOutItem}>
              <LogOut size={16} />退出登录
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <Dialog.Root open={projectAction !== null} onOpenChange={(open) => {
        if (!open && !projectActionBusy) { setProjectAction(null); setProjectActionError(null); }
      }}>
        <Dialog.Portal>
          <Dialog.Overlay className={styles.projectDialogOverlay} />
          <Dialog.Content className={styles.projectDialog}>
            <Dialog.Title className={styles.projectDialogTitle}>
              {projectAction?.kind === "rename" ? "重命名工作区" : "删除工作区"}
            </Dialog.Title>
            <Dialog.Description className={styles.projectDialogDescription}>
              {projectAction?.kind === "rename"
                ? `修改“${projectAction.name}”的名称。`
                : `删除“${projectAction?.name ?? ""}”后，其中的文档和文件关联会被清理，工作区将无法恢复。`}
            </Dialog.Description>
            {projectAction?.kind === "rename" ? (
              <form onSubmit={(event) => void submitRename(event)}>
                <input autoFocus className={styles.projectDialogInput} aria-label="工作区名称"
                  maxLength={80} value={renameDraft} disabled={projectActionBusy}
                  onChange={(event) => setRenameDraft(event.target.value)} />
                {projectActionError && <p role="alert" className={styles.projectDialogError}>{projectActionError}</p>}
                <div className={styles.projectDialogActions}>
                  <Dialog.Close asChild><button type="button" className={styles.projectDialogCancel} disabled={projectActionBusy}>取消</button></Dialog.Close>
                  <button type="submit" className={styles.projectDialogPrimary} disabled={!renameDraft.trim() || projectActionBusy}>保存</button>
                </div>
              </form>
            ) : (
              <div>
                {projectActionError && <p role="alert" className={styles.projectDialogError}>{projectActionError}</p>}
                <div className={styles.projectDialogActions}>
                  <Dialog.Close asChild><button type="button" className={styles.projectDialogCancel} disabled={projectActionBusy}>取消</button></Dialog.Close>
                  <button type="button" className={styles.projectDialogDanger} disabled={projectActionBusy}
                    onClick={() => void confirmDelete()}>确认删除</button>
                </div>
              </div>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </aside>
  );
}
