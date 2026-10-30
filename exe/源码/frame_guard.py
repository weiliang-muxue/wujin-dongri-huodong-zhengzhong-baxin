"""Pre-input rejection: discard this observation and capture a fresh frame.

An input callback must never propagate RetryFrame after it has submitted input.
The runners may safely retry a decision only while no input has been sent.
"""


class RetryFrame(RuntimeError):
    pass
