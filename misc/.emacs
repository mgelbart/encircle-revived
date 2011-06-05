;; Use spaces instead of tabs
(setq-default indent-tabs-mode nil)

;; Indent blocks by only two spaces (default is 4).
(setq-default c-basic-offset 2)

;; Typically always modifying files in a subdir
;; but the Makefile is in the root.
(setq compile-command "cd ..; make -k")