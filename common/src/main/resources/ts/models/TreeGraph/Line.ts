import {TreeUtils} from "@common/utils/tree";

export class Line {
    startX: number;
    startY: number;
    endX: number;
    endY: number;

    constructor(startX: number, startY: number, endX: number, endY: number) {
        this.startX = startX ? startX : null;
        this.startY = startY ? startY : null;
        this.endX = endX ? endX : null;
        this.endY = endY ? endY : null;
    }

    linesIntersect = (line: Line) : boolean => {
        return TreeUtils.intersects(this.startX, this.startY, this.endX, this.endY, line.startX, line.startY, line.endX, line.endY);
    }
}

export class Lines {
    all: Line[];

    constructor() {
        this.all = [];
    }
}