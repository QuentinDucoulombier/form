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
}

export class Lines {
    all: Line[];

    constructor() {
        this.all = [];
    }
}