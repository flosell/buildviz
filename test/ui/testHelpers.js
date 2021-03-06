"use strict";

const testHelpers = (function () {
    const module = {};

    const dateImpl = window.Date;

    module.mockDateToday = function (today) {
        window.Date = function () {
            if (arguments.length === 0) {
                return new dateImpl(today);
            } else {
                const factoryFunction = dateImpl.bind.apply(dateImpl, [null].concat(Array.prototype.slice.call(arguments)));
                return new factoryFunction();
            }
        };
    };

    module.hoverOver = function (element) {
        const elementRect = element.getBoundingClientRect();
        const event = new MouseEvent('mouseover', {
            "bubbles": true,
            "cancelable": false,
            "clientX": elementRect.left,
            "clientY": elementRect.top
        });
        setTimeout(function () { element.dispatchEvent(event); }, 0);
    };

    module.selectTimespan = function (buttonText) {
        const button = Array.prototype.find.call(document.querySelectorAll('button'), function (element) {
            return element.innerText == buttonText;
        });
        button.click();
    };

    return module;
} ());
