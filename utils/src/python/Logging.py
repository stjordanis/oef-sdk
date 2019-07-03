import logging
import functools
import colorlog
import functools

_HANDLERS = {}
_MIN_LEVEL = logging.INFO


def configure(level=logging.ERROR, file="", file_level=logging.INFO):
    global _MIN_LEVEL, _HANDLERS
    log_format = '%(asctime)s, %(levelname)s:  - %(name)s ] %(message)s'
    bold_seq = '\033[1m'  #f'{bold_seq} '
    colorlog_format = (
        '%(log_color)s '
        f'{log_format}'
    )
    log_formatter = colorlog.ColoredFormatter(colorlog_format)

    if "console" not in _HANDLERS:
        console_handler = colorlog.StreamHandler()
        console_handler.setFormatter(log_formatter)
        console_handler.setLevel(level)
        _HANDLERS["console"] = console_handler

    if len(file) > 0 and file not in _HANDLERS:
        file_handler = logging.FileHandler(file, mode='w')
        file_handler.setLevel(file_level)
        file_handler.setFormatter(log_formatter)
        _HANDLERS[file] = file_handler
    else:
        console = _HANDLERS.get("console", None)
        if console:
            console.setLevel(file_level)

    if file_level < level:
        _MIN_LEVEL = file_level
    else:
        _MIN_LEVEL = level
    #colorlog.basicConfig(format=colorlog_format, level=level)
    #logging.basicConfig(format=log_format, level=level)


class Logger:
    def __init__(self, global_name, handlers, local_name=None, level=logging.INFO):
        self._logger = None
        self._global_name = global_name
        self._local_name = local_name
        self._handlers = handlers
        self._level = level
        self._set_logger()
        self._target_obj = None

    def _set_logger(self):
        name = self._global_name
        if self._local_name is not None:
            name += ": {}".format(self._local_name)
        self._logger = colorlog.getLogger(name)
        self._logger.setLevel(self._level)
        for key, handler in self._handlers.items():
            self._logger.addHandler(handler)

    def __getattr__(self, item):
        return getattr(self._logger, item)

    def update_local_name(self, local_name):
        if local_name == self._local_name:
            return
        self._local_name = local_name
        self._set_logger()
        if self._target_obj is not None:
            self.expose_log_calls(self._target_obj)

    def expose_log_calls(self, target):
        def wrapper(func_name):
            func = getattr(self._logger, func_name)

            @functools.wraps(func)
            def inner_wrapper(*args, **kwargs):
                if len(args) > 1:
                    if isinstance(args[0], str):
                        if args[0].find("%") == -1:
                            return func(("{} "*len(args)).format(*args), **kwargs)
                    else:
                        return func(("{} " * len(args)).format(*args), **kwargs)
                return func(*args, **kwargs)
            return inner_wrapper
        for func_name in ["debug", "info", "warning", "error", "critical", "exception"]:
            setattr(target, func_name,  wrapper(func_name))
        self._target_obj = target


def has_logger(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        global _HANDLERS, _MIN_LEVEL
        self = args[0]
        name = self.__class__.__name__
        local_name = None
        if "id" in kwargs:
            local_name = kwargs["id"]
        self.log = Logger(name, _HANDLERS, local_name, level=_MIN_LEVEL)
        self.log.expose_log_calls(self)
        return func(*args, **kwargs)
    return wrapper


def get_logger(name):
    global _HANDLERS, _MIN_LEVEL
    return Logger(name, _HANDLERS, level=_MIN_LEVEL)
