def destroy_window(widget,root=False):
    """Cancel Tcl timers before destroying a view, including CustomTkinter timers."""
    commands=set()
    def collect(w):
        commands.update(getattr(w,'_tclCommands',None) or [])
        for child in w.winfo_children():collect(child)
    try:
        collect(widget)
        for timer in widget.tk.call('after','info'):
            info=widget.tk.call('after','info',timer)
            if root or (info and str(info[0]) in commands):widget.tk.call('after','cancel',timer)
    except Exception:pass
    widget.destroy()
