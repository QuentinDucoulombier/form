import {idiom, ng} from 'entcore';
import {
    Arrow,
    Arrows,
    Form,
    FormElement,
    FormElements,
    Line,
    Question,
    QuestionChoices,
    Section
} from "../models";
import * as d3 from 'd3';
import * as dagreD3 from 'dagre-d3';
import {IconUtils} from "@common/utils/icon";
import {Mix} from "entcore-toolkit";
import {TreeUtils} from "@common/utils/tree";
import {Pages} from "@common/core/enums";

interface ViewModel {
    form: Form;
    formElements: FormElements;
    loading: boolean;
    backToEditor() : void;
    $onInit() : Promise<void>;
}

export const formTreeViewController = ng.controller('FormTreeViewController', ['$scope',
    function ($scope) {

        const vm: ViewModel = this;
        let mainGraph: any = null;
        vm.form = new Form();
        vm.formElements = new FormElements();

        vm.$onInit = async () : Promise<void> => {
            vm.loading = true;
            $scope.safeApply();
            vm.form = $scope.form;
            await vm.formElements.sync(vm.form.id);
            initD3Dagre();

            vm.loading = false;
            $scope.safeApply();
        };

        // Functions
        vm.backToEditor = () : void => {
            $scope.redirectTo(`/form/${vm.form.id}/edit`);
        };

        const initD3Dagre = () : void => {
            // Init nodes and edges
            let nodes: any[] = initNodes(); // List of form elements
            let edgeList: any[] = initEdgeList(); // List of links between form elements

            // Create a renderer
            let render: any = new dagreD3.render();

            // Set up an SVG group so that we can translate the final graph.
            let svg: any = d3.select("svg");
            let inner: any = svg.select("g");

            //Set up zoom support
            let zoom: d3.ZoomBehavior<Element, unknown> = d3.zoom().on("zoom", function(e) {
                inner.attr("transform", e.transform);
            });
            svg.call(zoom);

            // Run the renderer. This is what draws the final graph.
            render_graph(render, nodes, edgeList, inner, svg);

            // Center the graph
            const vw: number = window.innerWidth - (window.innerWidth * 8 / 100);
            const vh: number = window.innerHeight - (window.innerHeight * 30 / 100);
            svg.attr('width', vw).attr('height', vh);
            let initialScale: number = 0.75;
            if(mainGraph != null){
                svg.call(zoom.transform, d3.zoomIdentity.translate((svg.attr("width") - mainGraph.graph().width * initialScale) / 2, 20).scale(initialScale));
            }
        }

        // List initializations
        const initNodes = () : any[] => {
            let recapElement: any = {
                id: null,
                form_id: vm.form.id,
                title: idiom.translate("formulaire.end.form")
            };
            let formElements: FormElement[] = [...vm.formElements.all, Mix.castAs(FormElement, recapElement)];
            for (let formElement of formElements) {
                formElement.setTreeNodeLabel();
            }
            return formElements;
        }

        const initEdgeList = () : any[] => {
            let formElementLinks: any[] = [];
            for (let formElement of vm.formElements.all) {
                if (formElement instanceof Section) {
                    let conditionalQuestion: Question = formElement.questions.all.find((q: Question) => q.conditional);
                    if (conditionalQuestion) {
                        addChoicesLink(formElement, conditionalQuestion.choices, formElementLinks);
                    }
                    else {
                        let nextFormElementId: number = formElement.getNextFormElementId(vm.formElements);
                        addFormElementLink(formElement.id, nextFormElementId, formElementLinks);
                    }
                }
                else if (formElement instanceof Question && formElement.conditional) {
                    addChoicesLink(formElement, formElement.choices, formElementLinks);
                }
                else {
                    let nextFormElementId: number = formElement.getNextFormElementId(vm.formElements);
                    addFormElementLink(formElement.id, nextFormElementId, formElementLinks);
                }
            }

            return formElementLinks;
        }

        const addFormElementLink = (formElementId: number, nextFormElementId: number, formElementLinks: any[]) : void => {
            let formElementLink: any[] = [`${formElementId}`, `${nextFormElementId}`, {"label":""}];
            formElementLinks.push(formElementLink);
        }

        const addChoicesLink = (formElement: FormElement, choices: QuestionChoices, formElementLinks: any[]) : void => {
            for (let choice of choices.all) {
                let nextFormElement: FormElement = choice.getNextFormElement(vm.formElements);
                let nextFormElementId: number = nextFormElement ? nextFormElement.id : null;
                addFormElementLink(formElement.id, nextFormElementId, formElementLinks);
            }
        }

        // Rendering

        const render_graph = (render, nodes, edgeList, inner, svg) : void => {
            let nbTries: number = 100; // try 100 times, if optimal not found, give up
            let iter_cnt: number = 0;
            let optimalArray: any[] | undefined;
            let best_result: number | undefined;

            while (nbTries--) {
                let list: any[] = TreeUtils.shuffle(edgeList);
                if (!optimalArray) optimalArray = list;
                setNodesAndEdges(nodes, edgeList, render, inner);

                let arrows: Arrows = extractArrows(svg);
                let collisionArrowsCnt: number = arrows.countNbCollisions();

                // If we found optimal option, we stop here and keep this option
                // console.log("collisions ", collisionArrowsCnt);
                if (collisionArrowsCnt == 0) {
                    optimalArray = list.slice();
                    // console.log("Iteration cnt ", iter_cnt);
                    break;
                }

                // Init best_result variable
                if (iter_cnt == 0) best_result = collisionArrowsCnt;

                // Update best_result if new option is better
                if (collisionArrowsCnt < best_result) {
                    best_result = collisionArrowsCnt;
                    optimalArray = list.slice();
                }

                iter_cnt++;
            }

            // We render the best option we found
            // console.log("best_result found : ", best_result);
            setNodesAndEdges(nodes, optimalArray ? optimalArray : edgeList, render, inner);
        }

        const setNodesAndEdges = (nodes, edges, render, inner) : void => {
            let g = new dagreD3.graphlib.Graph().setGraph({});
            for (let node of nodes) {
                g.setNode(node.id, {
                    labelType: "html",
                    label: `${getHtmlNode(node)}`
                });
            }
            for (let edge of edges) {
                g.setEdge.apply(g, edge);
            }
            g.graph().rankdir = "TB"; // Direction of the flow of the graph TopBottom (TB) or LeftRight (LR)...
            g.graph().nodesep = 60; // Space between each node
            mainGraph = g;
            render(inner, g);
        }

        const extractArrows = (svg: any) : Arrows => {
            let nn = svg.select(".edgePaths");
            let paths = nn._groups ? nn._groups[0][0] : nn[0][0];
            let fc = paths.firstChild;
            let arrows: Arrows = new Arrows();
            while (fc) {
                let path = fc.firstChild.getAttribute("d");
                let coords = path.split(/,|L/)
                    .map((c: string) => {
                        let n: string = c;
                        if ((c[0]=="M" || c[0]=="L")) n = c.substring(1);
                        return parseFloat(n);
                    })

                let arrow: Arrow = new Arrow();
                for (let i = 0; i <= coords.length - 4; i+=2) {
                    arrow.lines.all.push(new Line(coords[i], coords[i+1], coords[i+2], coords[i+3]));
                }
                arrows.all.push(arrow);

                fc = fc.nextSibling;
            }

            return arrows;
        }

        // HTML

        const getHtmlNode = (formElement: FormElement) : string => {
            if (formElement instanceof Question) {
                return `<div class="tree-view-question">
                            <img src="${IconUtils.displayTypeIcon(formElement.question_type)}"/>
                            <div class="title">${formElement.title}</div>
                        </div>`;
            }
            else if (formElement instanceof Section) {
                return `<div class="tree-view-section">
                            <div class="top twelve">
                                <span class="title">${formElement.title}</span>
                            </div>
                            
                            <div class="main twelve">
                                ${repeatHtmlOnQuestions(formElement.questions.all)}
                            </div>
                        </div>`;
            }
            else {
                return `<div class="tree-view-section">
                            <div class="top no-main twelve">
                                <span class="title">${formElement.title}</span>
                            </div>
                        </div>`;
            }
        }

        const repeatHtmlOnQuestions = (questions: Question[]) : string => {
            let questionsTemplate: string = "";
            for (let question of questions) {
                questionsTemplate += getHtmlNode(question);
            }

            return questionsTemplate;
        }
    }]);